{
  inputs = {
    typelevel-nix.url = "github:typelevel/typelevel-nix";
    nixpkgs.follows = "typelevel-nix/nixpkgs";
    oldNixpkgs.url = "github:NixOS/nixpkgs/nixos-23.05";
    flake-utils.follows = "typelevel-nix/flake-utils";
  };

  outputs = { self, nixpkgs, oldNixpkgs, flake-utils, typelevel-nix }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        graalOverlay = final: prev: rec {
          holyGraal = with oldNixpkgs.legacyPackages.${system}; graalvm17-ce.override {
            products = with graalvmCEPackages; [
              js-installable-svm-java17
              native-image-installable-svm-java17
            ];
          };
          jdk = holyGraal;
          jre = holyGraal;
        };
        overlays = [ typelevel-nix.overlay graalOverlay ];
        pkgs = import nixpkgs {
          inherit system overlays;
          config.allowUnfree = true;
        };
        pkgsCross = import nixpkgs {
          inherit system overlays;
          crossSystem = nixpkgs.lib.systems.examples.aarch64-multiplatform;
        };
        sandboxfs = pkgs.sandboxfs;
      in
      with pkgs;
      {
        devShells.default = devshell.mkShell {
          commands = [
            {
               name = "aarch64-linux-gnu-gcc";
               package = pkgsCross.buildPackages.gcc;
               help = "Cross-compiler for AArch64 targets";
            }
            {
              name = "docker";
              package = docker;
            }
            {
              name = "gralde";
              package = gradle_7;
            }
          ] ++ pkgs.lib.optionals (pkgs.system == "x86_64-darwin" || pkgs.system == "aarch64-darwin") [
            {
              name = "sandboxfs";
              package = sandboxfs;
              help = "Sandboxfs for MacOS FUSE";
            }
          ];
          imports = [ typelevel-nix.typelevelShell ];
          name = "f1r3fly-fs-shell";
          typelevelShell = {
		        jdk.package = holyGraal;
          };
        };
      }
    );
}