use rcgen::{CertifiedKey, generate_simple_self_signed};
use std::fs;
use std::path::Path;
use tracing::{info, warn};

use crate::config::TlsConfig;

/// Ensures TLS certificates exist, generating them if needed.
/// Returns the paths to the certificate and key files.
pub fn ensure_certificates(config: &TlsConfig) -> Result<(String, String), Box<dyn std::error::Error>> {
    let cert_path = Path::new(&config.cert_path);
    let key_path = Path::new(&config.key_path);

    // Check if certificates already exist
    if cert_path.exists() && key_path.exists() {
        info!("Using existing TLS certificates");
        return Ok((config.cert_path.clone(), config.key_path.clone()));
    }

    // Generate new certificates if auto_generate is enabled
    if !config.auto_generate {
        return Err("TLS certificates not found and auto_generate is disabled".into());
    }

    info!("Generating self-signed TLS certificates...");
    generate_certificates(&config.cert_path, &config.key_path)?;

    Ok((config.cert_path.clone(), config.key_path.clone()))
}

/// Generates a self-signed certificate and saves it to the specified paths.
fn generate_certificates(cert_path: &str, key_path: &str) -> Result<(), Box<dyn std::error::Error>> {
    // Subject Alternative Names for the certificate
    let subject_alt_names = vec![
        "localhost".to_string(),
        "127.0.0.1".to_string(),
        "::1".to_string(),
        // Common LAN IP ranges
        "192.168.0.1".to_string(),
        "192.168.1.1".to_string(),
        "10.0.0.1".to_string(),
    ];

    let CertifiedKey { cert, key_pair } = generate_simple_self_signed(subject_alt_names)?;

    // Create parent directories if they don't exist
    if let Some(parent) = Path::new(cert_path).parent() {
        fs::create_dir_all(parent)?;
    }
    if let Some(parent) = Path::new(key_path).parent() {
        fs::create_dir_all(parent)?;
    }

    // Write certificate and key
    fs::write(cert_path, cert.pem())?;
    fs::write(key_path, key_pair.serialize_pem())?;

    info!("TLS certificates generated:");
    info!("  Certificate: {}", cert_path);
    info!("  Private key: {}", key_path);

    // Also output the certificate for bundling in Android
    warn!("To use certificate pinning in Android, copy the certificate to your app's assets.");

    Ok(())
}

/// Returns the certificate in DER format for embedding in clients.
pub fn get_certificate_der(cert_path: &str) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
    let pem_content = fs::read_to_string(cert_path)?;

    // Parse PEM and extract DER
    let pem = pem_content
        .lines()
        .filter(|line| !line.starts_with("-----"))
        .collect::<String>();

    let der = base64_decode(&pem)?;
    Ok(der)
}

fn base64_decode(input: &str) -> Result<Vec<u8>, Box<dyn std::error::Error>> {
    // Simple base64 decode
    const ALPHABET: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    let input = input.replace(['\n', '\r', ' '], "");
    let mut result = Vec::new();
    let mut buffer = 0u32;
    let mut bits = 0;

    for c in input.chars() {
        if c == '=' {
            break;
        }
        let value = ALPHABET.iter().position(|&b| b == c as u8)
            .ok_or("Invalid base64 character")? as u32;
        buffer = (buffer << 6) | value;
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            result.push((buffer >> bits) as u8);
            buffer &= (1 << bits) - 1;
        }
    }

    Ok(result)
}
