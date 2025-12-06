use std::process::Command;

fn main() {
    let dashboard_dir = "../dashboard";

    // Rerun if dashboard source files change
    println!("cargo:rerun-if-changed={}/src", dashboard_dir);
    println!("cargo:rerun-if-changed={}/index.html", dashboard_dir);
    println!("cargo:rerun-if-changed={}/package.json", dashboard_dir);

    // Run npm build
    let status = Command::new("npm")
        .args(["run", "build"])
        .current_dir(dashboard_dir)
        .status()
        .expect("Failed to run npm build - is Node.js installed?");

    if !status.success() {
        panic!("Dashboard build failed");
    }
}
