{ pkgs, lib, config, inputs, ... }:

let
  pkgs-unstable = import inputs.nixpkgs-unstable {
    system = pkgs.stdenv.system;
  };
in

{
  name = "b-of-f";

  packages = [
    pkgs.git
    pkgs-unstable.just
    pkgs-unstable.scala-cli
  ];

  languages.java = {
    enable = true;
    jdk.package = pkgs-unstable.jdk25_headless;
  };

  env = {
    JAVA_OPTS="--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED ";
    SBT_OPTS="--sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED ";
  };

  enterShell = ''
    echo JAVA_HOME=$JAVA_HOME
    export PATH=$PATH
  '';

  enterTest = ''
  '';
}
