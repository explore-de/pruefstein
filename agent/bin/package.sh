#!/usr/bin/env bash
#
# Package a built native agent into a relocatable archive, the shape a
# Homebrew formula (or anything else that downloads a tarball) can consume.
#
#   ./agent/bin/package.sh                 package whatever is in target/
#   ./agent/bin/package.sh --build         build the native binary first
#
# Writes agent/dist/pruefstein-agent-<version>-<os>-<arch>.tar.gz and a
# .sha256 next to it. The archive holds the binary under its command name,
# plus the licence and the agent README, so an unpacked copy explains itself.
#
# Deliberately not the same thing as bin/install.sh: that one links the
# command back into this working tree, which is right for somebody hacking on
# the agent and useless to somebody installing a release.
#
set -euo pipefail

src="${BASH_SOURCE[0]}"
while [[ -L "$src" ]]; do
	dir="$(cd -P "$(dirname "$src")" && pwd)"
	src="$(readlink "$src")"
	[[ "$src" != /* ]] && src="$dir/$src"
done
module="$(cd -P "$(dirname "$src")/.." && pwd)"
root="$(cd -P "$module/.." && pwd)"

command_name="pruefstein-agent"

if [[ "${1:-}" == "--build" ]]; then
	echo "Building the native binary — this takes a few minutes…"
	(cd "$module" && ./mvnw package -Dnative -DskipTests -B)
fi

binary="$(find "$module/target" -maxdepth 1 -type f -name 'pruefstein-agent-*-runner' 2>/dev/null | head -n1 || true)"
if [[ -z "$binary" || ! -x "$binary" ]]; then
	cat >&2 <<-MSG
	package.sh: no native binary in $module/target

	A release archive has to be a binary somebody can run without a JVM, so a
	jar build is not enough here. Build one with:

	  ./agent/bin/package.sh --build          # needs GraalVM on JAVA_HOME
	MSG
	exit 1
fi

# The version Maven stamped the binary with, rather than a number repeated
# here that would drift from the pom the first time it changed.
version="$(basename "$binary")"
version="${version#pruefstein-agent-}"
version="${version%-runner}"

# uname's vocabulary, mapped to the one release archives and Homebrew bottles
# use: arm64/amd64 rather than aarch64/x86_64, lowercase os.
os="$(uname -s | tr '[:upper:]' '[:lower:]')"
case "$(uname -m)" in
	arm64 | aarch64) arch="arm64" ;;
	x86_64 | amd64) arch="amd64" ;;
	*) arch="$(uname -m)" ;;
esac

dist="$module/dist"
name="$command_name-$version-$os-$arch"
staging="$dist/$name"

rm -rf "$staging"
mkdir -p "$staging"
cp "$binary" "$staging/$command_name"
chmod +x "$staging/$command_name"
cp "$root/LICENSE" "$staging/LICENSE"
cp "$module/README.md" "$staging/README.md"

archive="$dist/$name.tar.gz"
rm -f "$archive" "$archive.sha256"
# Ownership, mtimes, member order and the gzip header are all pinned, so the
# same binary packages to the same bytes every time. That is as far as it
# goes: native-image bakes build paths into the binary itself, so two builds
# of the same commit do not agree — what is deterministic here is the
# archiving, which is what stops a re-package from inventing a new checksum.
touch -t 200001010000 "$staging/$command_name" "$staging/LICENSE" "$staging/README.md" "$staging"
tar -C "$dist" \
	--uid 0 --gid 0 --numeric-owner \
	-cf - "$name/LICENSE" "$name/README.md" "$name/$command_name" \
	| gzip -n -9 > "$archive"
rm -rf "$staging"

if command -v shasum >/dev/null 2>&1; then
	(cd "$dist" && shasum -a 256 "$name.tar.gz" > "$name.tar.gz.sha256")
else
	(cd "$dist" && sha256sum "$name.tar.gz" > "$name.tar.gz.sha256")
fi

echo "Packaged $archive"
cat "$archive.sha256"
