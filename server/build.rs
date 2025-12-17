use std::process::Command;

fn main() {
    println!("cargo:rerun-if-changed=dashboard/src");
    println!("cargo:rerun-if-changed=dashboard/index.html");
    println!("cargo:rerun-if-changed=dashboard/package.json");

    // Skip npm build during cargo publish verification
    // (it would create node_modules which cargo doesn't allow)
    if std::env::var("CARGO_PRIMARY_PACKAGE").is_err() {
        return;
    }

    // Try to build the dashboard if npm is available
    // If it fails, we'll use the existing dist/ files (which should be committed)
    let dashboard_dir = std::path::Path::new("dashboard");

    if !dashboard_dir.exists() {
        println!("cargo:warning=Dashboard directory not found, skipping build");
        return;
    }

    // Check if node_modules exists, if not try npm install
    let node_modules = dashboard_dir.join("node_modules");
    if !node_modules.exists() {
        let _ = Command::new("npm")
            .arg("install")
            .current_dir(dashboard_dir)
            .status();
    }

    // Try to build
    let status = Command::new("npm")
        .args(["run", "build"])
        .current_dir(dashboard_dir)
        .status();

    match status {
        Ok(s) if s.success() => {
            println!("cargo:warning=Dashboard built successfully");
        }
        Ok(_) => {
            println!("cargo:warning=Dashboard build failed, using existing dist/");
        }
        Err(_) => {
            println!("cargo:warning=npm not found, using existing dist/");
        }
    }
}
