//! Protocol v2: TLS-bound pairing and bounded JSON framing.
use anyhow::{bail, ensure, Context, Result};
use hmac::{Hmac, Mac};
use rand::RngCore;
use rustls::{
    client::danger::{HandshakeSignatureValid, ServerCertVerified, ServerCertVerifier},
    pki_types::{CertificateDer, ServerName, UnixTime},
    DigitallySignedStruct, SignatureScheme,
};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::sync::Arc;
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt};
use tokio::net::TcpStream;
use tokio_rustls::{client::TlsStream, TlsConnector};

pub const MAX_FRAME: usize = 1024 * 1024;
pub const EXPORTER: &[u8] = b"EXPORTER-Androidoscopy-v2";

pub fn hmac(secret: &[u8], data: &[u8]) -> Vec<u8> {
    let mut mac = Hmac::<Sha256>::new_from_slice(secret).expect("HMAC key");
    mac.update(data);
    mac.finalize().into_bytes().to_vec()
}
pub fn random() -> [u8; 32] {
    let mut v = [0; 32];
    rand::thread_rng().fill_bytes(&mut v);
    v
}
pub fn code(secret: &[u8], client: &[u8], server: &[u8]) -> String {
    let digest = hmac(secret, &[client, server].concat());
    format!(
        "{:08}",
        u32::from_be_bytes(digest[..4].try_into().unwrap()) % 100_000_000
    )
}
pub fn proof(secret: &[u8], exporter: &[u8], session: &str) -> Vec<u8> {
    hmac(
        secret,
        &[format!("resume:{session}:").as_bytes(), exporter].concat(),
    )
}
pub async fn read_frame<R: AsyncRead + Unpin>(reader: &mut R) -> Result<Value> {
    let length = reader.read_u32().await? as usize;
    ensure!((1..=MAX_FRAME).contains(&length), "invalid frame size");
    let mut bytes = vec![0; length];
    reader.read_exact(&mut bytes).await?;
    let value: Value = serde_json::from_slice(&bytes)?;
    ensure!(value.is_object(), "expected object");
    Ok(value)
}
pub async fn write_frame<W: AsyncWrite + Unpin>(writer: &mut W, value: &Value) -> Result<()> {
    let bytes = serde_json::to_vec(value)?;
    ensure!(bytes.len() <= MAX_FRAME, "frame too large");
    writer.write_u32(bytes.len() as u32).await?;
    writer.write_all(&bytes).await?;
    writer.flush().await?;
    Ok(())
}

/// An unpaired TLS connection is quarantined until SAS authorization. Handshake
/// signatures are still verified; this is not a global trust-all TLS policy.
#[derive(Debug)]
struct PairingVerifier;
impl ServerCertVerifier for PairingVerifier {
    fn verify_server_cert(
        &self,
        _: &CertificateDer<'_>,
        _: &[CertificateDer<'_>],
        _: &ServerName<'_>,
        _: &[u8],
        _: UnixTime,
    ) -> Result<ServerCertVerified, rustls::Error> {
        Ok(ServerCertVerified::assertion())
    }
    fn verify_tls12_signature(
        &self,
        m: &[u8],
        c: &CertificateDer<'_>,
        d: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls12_signature(
            m,
            c,
            d,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }
    fn verify_tls13_signature(
        &self,
        m: &[u8],
        c: &CertificateDer<'_>,
        d: &DigitallySignedStruct,
    ) -> Result<HandshakeSignatureValid, rustls::Error> {
        rustls::crypto::verify_tls13_signature(
            m,
            c,
            d,
            &rustls::crypto::ring::default_provider().signature_verification_algorithms,
        )
    }
    fn supported_verify_schemes(&self) -> Vec<SignatureScheme> {
        rustls::crypto::ring::default_provider()
            .signature_verification_algorithms
            .supported_schemes()
    }
}

pub struct Connection {
    pub stream: TlsStream<TcpStream>,
    pub hello: Value,
    pub exporter: Vec<u8>,
    pub fingerprint: String,
}
impl Connection {
    pub async fn open(address: &str) -> Result<Self> {
        let config = rustls::ClientConfig::builder_with_provider(Arc::new(
            rustls::crypto::ring::default_provider(),
        ))
        .with_protocol_versions(&[&rustls::version::TLS13])?
        .dangerous()
        .with_custom_certificate_verifier(Arc::new(PairingVerifier))
        .with_no_client_auth();
        let tcp = TcpStream::connect(address).await?;
        tcp.set_nodelay(true)?;
        let mut stream = TlsConnector::from(Arc::new(config))
            .connect(ServerName::try_from("androidoscopy")?, tcp)
            .await?;
        let exporter = stream
            .get_ref()
            .1
            .export_keying_material(vec![0; 32], EXPORTER, None)?;
        let cert = stream
            .get_ref()
            .1
            .peer_certificates()
            .and_then(|cs| cs.first())
            .context("missing certificate")?;
        let fingerprint = hex::encode(Sha256::digest(cert.as_ref()));
        let hello = read_frame(&mut stream).await?;
        ensure!(
            hello["type"] == "HELLO" && hello["version"] == 2,
            "unsupported device protocol"
        );
        Ok(Self {
            stream,
            hello,
            exporter,
            fingerprint,
        })
    }
    pub async fn begin_pairing(&mut self, peer: &str) -> Result<String> {
        let client = random();
        write_frame(&mut self.stream, &json!({"type":"PAIR","peer":peer,"commitment":hex::encode(hmac(&self.exporter, &client))})).await?;
        let response = read_frame(&mut self.stream).await?;
        ensure!(response["type"] == "CHALLENGE", "pairing rejected");
        let server = hex::decode(response["nonce"].as_str().context("missing challenge")?)?;
        ensure!(server.len() == 32, "invalid challenge");
        write_frame(
            &mut self.stream,
            &json!({"type":"REVEAL","nonce":hex::encode(client)}),
        )
        .await?;
        Ok(code(&self.exporter, &client, &server))
    }
    pub async fn resume(&mut self, peer: &str, secret: &[u8]) -> Result<()> {
        let session = self.hello["session"].as_str().context("missing session")?;
        write_frame(&mut self.stream, &json!({"type":"RESUME","peer":peer,"proof":hex::encode(proof(secret, &self.exporter, session))})).await
    }
    pub async fn authorized(&mut self, expected_secret: Option<&[u8]>) -> Result<Value> {
        let value = read_frame(&mut self.stream).await?;
        ensure!(
            value["type"] == "AUTHORIZED" && value["session"] == self.hello["session"],
            "authorization failed"
        );
        let secret = hex::decode(value["credential"].as_str().context("missing credential")?)?;
        ensure!(secret.len() == 32, "invalid credential");
        if let Some(expected) = expected_secret {
            if expected != secret {
                bail!("peer failed mutual authentication");
            }
        }
        Ok(value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn pairing_vectors() {
        assert_eq!(
            hex::encode(hmac(&[0; 32], &[1; 32])),
            "80a09de3bfe30da90116e588ade2f812d49b55625be8b4abbff775fa5a5a74e9"
        );
        assert_ne!(
            code(&[0; 32], &[1; 32], &[2; 32]),
            code(&[1; 32], &[1; 32], &[2; 32])
        );
        assert_ne!(
            proof(&[3; 32], &[4; 32], "one"),
            proof(&[3; 32], &[4; 32], "two")
        );
    }
    #[tokio::test]
    async fn oversized_frames_are_rejected_before_allocation() {
        let bytes = ((MAX_FRAME + 1) as u32).to_be_bytes();
        assert!(read_frame(&mut bytes.as_slice()).await.is_err());
    }
    #[tokio::test]
    async fn typed_json_round_trip() {
        let (mut a, mut b) = tokio::io::duplex(4096);
        let value = json!({"a":[true,42,null,{"b":1.25}]});
        write_frame(&mut a, &value).await.unwrap();
        assert_eq!(read_frame(&mut b).await.unwrap(), value);
    }

    #[tokio::test]
    async fn tls_pairing_exporters_match_and_stale_authorization_is_rejected() {
        let certificate = rcgen::generate_simple_self_signed(vec!["androidoscopy".into()]).unwrap();
        let server_config = rustls::ServerConfig::builder_with_provider(Arc::new(
            rustls::crypto::ring::default_provider(),
        ))
        .with_protocol_versions(&[&rustls::version::TLS13])
        .unwrap()
        .with_no_client_auth()
        .with_single_cert(
            vec![certificate.cert.der().clone()],
            rustls::pki_types::PrivatePkcs8KeyDer::from(certificate.key_pair.serialize_der())
                .into(),
        )
        .unwrap();
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let server = tokio::spawn(async move {
            let (tcp, _) = listener.accept().await.unwrap();
            let mut stream = tokio_rustls::TlsAcceptor::from(Arc::new(server_config))
                .accept(tcp)
                .await
                .unwrap();
            let exporter = stream
                .get_ref()
                .1
                .export_keying_material(vec![0; 32], EXPORTER, None)
                .unwrap();
            write_frame(
                &mut stream,
                &json!({"type":"HELLO","version":2,"device":"device","session":"session"}),
            )
            .await
            .unwrap();
            let pair = read_frame(&mut stream).await.unwrap();
            let nonce = random();
            write_frame(
                &mut stream,
                &json!({"type":"CHALLENGE","nonce":hex::encode(nonce)}),
            )
            .await
            .unwrap();
            let reveal = read_frame(&mut stream).await.unwrap();
            let client = hex::decode(reveal["nonce"].as_str().unwrap()).unwrap();
            assert_eq!(pair["commitment"], hex::encode(hmac(&exporter, &client)));
            let expected = code(&exporter, &client, &nonce);
            write_frame(
                &mut stream,
                &json!({"type":"AUTHORIZED","session":"stale","credential":hex::encode(random())}),
            )
            .await
            .unwrap();
            expected
        });
        tokio::time::timeout(std::time::Duration::from_secs(5), async {
            let mut client = Connection::open(&address.to_string()).await.unwrap();
            let code = client.begin_pairing("peer").await.unwrap();
            assert!(client.authorized(None).await.is_err());
            assert_eq!(code, server.await.unwrap());
        })
        .await
        .unwrap();
    }
}
