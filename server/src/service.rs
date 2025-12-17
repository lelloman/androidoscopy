use std::fs;
use std::path::PathBuf;
use std::process::Command;

const SERVICE_NAME: &str = "androidoscopy";
const BINARY_NAME: &str = "androidoscopy-server";

pub fn install() -> Result<(), Box<dyn std::error::Error>> {
    let bin_dir = get_bin_dir()?;
    let service_dir = get_service_dir()?;

    // Ensure directories exist
    fs::create_dir_all(&bin_dir)?;
    fs::create_dir_all(&service_dir)?;

    // Copy binary
    let current_exe = std::env::current_exe()?;
    let target_bin = bin_dir.join(BINARY_NAME);

    println!("Installing binary to {}...", target_bin.display());
    fs::copy(&current_exe, &target_bin)?;

    // Make executable (should already be, but just in case)
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mut perms = fs::metadata(&target_bin)?.permissions();
        perms.set_mode(0o755);
        fs::set_permissions(&target_bin, perms)?;
    }

    // Generate service file
    let service_content = generate_service_file(&target_bin);
    let service_path = service_dir.join(format!("{}.service", SERVICE_NAME));

    println!("Installing service to {}...", service_path.display());
    fs::write(&service_path, service_content)?;

    // Reload systemd
    println!("Reloading systemd...");
    let status = Command::new("systemctl")
        .args(["--user", "daemon-reload"])
        .status()?;

    if !status.success() {
        return Err("Failed to reload systemd".into());
    }

    println!("\nInstallation complete!");
    println!("\nTo start the service:");
    println!("  systemctl --user start {}", SERVICE_NAME);
    println!("\nTo enable on login:");
    println!("  systemctl --user enable {}", SERVICE_NAME);
    println!("\nTo check status:");
    println!("  androidoscopy-server status");

    Ok(())
}

pub fn uninstall() -> Result<(), Box<dyn std::error::Error>> {
    let bin_dir = get_bin_dir()?;
    let service_dir = get_service_dir()?;
    let service_path = service_dir.join(format!("{}.service", SERVICE_NAME));
    let target_bin = bin_dir.join(BINARY_NAME);

    // Stop and disable service first
    println!("Stopping service...");
    let _ = Command::new("systemctl")
        .args(["--user", "stop", SERVICE_NAME])
        .status();

    println!("Disabling service...");
    let _ = Command::new("systemctl")
        .args(["--user", "disable", SERVICE_NAME])
        .status();

    // Remove service file
    if service_path.exists() {
        println!("Removing service file...");
        fs::remove_file(&service_path)?;
    }

    // Reload systemd
    let _ = Command::new("systemctl")
        .args(["--user", "daemon-reload"])
        .status();

    // Remove binary
    if target_bin.exists() {
        println!("Removing binary...");
        fs::remove_file(&target_bin)?;
    }

    println!("\nUninstallation complete!");
    println!("Note: Config files in ~/.androidoscopy/ were not removed.");

    Ok(())
}

pub fn status() -> Result<(), Box<dyn std::error::Error>> {
    let status = Command::new("systemctl")
        .args(["--user", "status", SERVICE_NAME])
        .status()?;

    std::process::exit(status.code().unwrap_or(1));
}

fn get_bin_dir() -> Result<PathBuf, Box<dyn std::error::Error>> {
    let home = dirs::home_dir().ok_or("Could not find home directory")?;
    Ok(home.join(".local").join("bin"))
}

fn get_service_dir() -> Result<PathBuf, Box<dyn std::error::Error>> {
    let config = dirs::config_dir().ok_or("Could not find config directory")?;
    Ok(config.join("systemd").join("user"))
}

fn generate_service_file(binary_path: &PathBuf) -> String {
    format!(
        r#"[Unit]
Description=Androidoscopy Server
After=network.target

[Service]
Type=simple
ExecStart={binary_path} run
Restart=on-failure
RestartSec=5

[Install]
WantedBy=default.target
"#,
        binary_path = binary_path.display(),
    )
}
