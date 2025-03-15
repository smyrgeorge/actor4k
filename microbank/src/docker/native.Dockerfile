FROM debian:bookworm-slim
COPY microbank/build/bin/linuxArm64/releaseExecutable/microbank.kexe /microbank.kexe
ENTRYPOINT ["/microbank.kexe"]