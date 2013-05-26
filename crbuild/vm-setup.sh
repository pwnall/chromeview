#!/bin/sh
# Idempotent VM setup / upgrade script.

set -o errexit  # Stop the script on the first error.
set -o nounset  # Catch un-initialized variables.

# Enable password-less sudo for the current user.
sudo sh -c "echo '$USER ALL=(ALL:ALL) NOPASSWD: ALL' > /etc/sudoers.d/$USER"

# Sun JDK 6.
if [ ! -f /usr/bin/javac ] ; then
  if [ ! -f ~/jdk6.bin ] ; then
    echo 'Please download the Linux x86 non-RPM JDK6 as jdk6.bin from'
    echo 'http://www.oracle.com/technetwork/java/javase/downloads/index.html'
    exit 1
  fi

  sudo mkdir -p /usr/lib/jvm
  cd /usr/lib/jvm
  sudo /bin/sh ~/jdk6.bin -noregister
  rm ~/jdk6.bin
  sudo update-alternatives --install /usr/bin/javac javac \
      /usr/lib/jvm/jdk1.6.0_*/bin/javac 50000
  sudo update-alternatives --config javac
  sudo update-alternatives --install /usr/bin/java java \
      /usr/lib/jvm/jdk1.6.0_*/bin/java 50000
  sudo update-alternatives --config java
  sudo update-alternatives --install /usr/bin/javaws javaws \
      /usr/lib/jvm/jdk1.6.0_*/bin/javaws 50000
  sudo update-alternatives --config javaws
  sudo update-alternatives --install /usr/bin/javap javap \
      /usr/lib/jvm/jdk1.6.0_*/bin/javap 50000
  sudo update-alternatives --config javap
  sudo update-alternatives --install /usr/bin/jar jar \
      /usr/lib/jvm/jdk1.6.0_*/bin/jar 50000
  sudo update-alternatives --config jar
  sudo update-alternatives --install /usr/bin/jarsigner jarsigner \
      /usr/lib/jvm/jdk1.6.0_*/bin/jarsigner 50000
  sudo update-alternatives --config jarsigner
  cd ~/
fi

# Update all system packages.
sudo apt-get update -qq
sudo apt-get -y dist-upgrade

# debconf-get-selections is useful for figuring out debconf defaults.
sudo apt-get install -y debconf-utils

# Quiet all package installation prompts.
sudo debconf-set-selections <<'END'
debconf debconf/frontend select Noninteractive
debconf debconf/priority select critical
END

# Git.
sudo apt-get install -y git

# Depot tools.
# http://dev.chromium.org/developers/how-tos/install-depot-tools
cd ~
if [ -d ~/depot_tools ] ; then
  cd ~/depot_tools
  git pull origin master
  cd ~
fi
if [ ! -d ~/depot_tools ] ; then
  git clone https://chromium.googlesource.com/chromium/tools/depot_tools.git
fi
if ! grep -q 'export PATH=$PATH:$HOME/depot_tools' ~/.bashrc ; then
  echo 'export PATH=$PATH:$HOME/depot_tools' >> ~/.bashrc
  export PATH=$PATH:$HOME/depot_tools
fi

# Subversion and git-svn.
sudo apt-get install -y git-svn subversion

# Chromium source.
# https://code.google.com/p/chromium/wiki/UsingGit
# http://dev.chromium.org/developers/how-tos/get-the-code
if [ ! -d ~/chromium ] ; then
  if [ ! -z $CHROMIUM_DIR ] ; then
    sudo mkdir -p "$CHROMIUM_DIR"
    sudo chown $USER "$CHROMIUM_DIR"
    chmod 0755 "$CHROMIUM_DIR"
    ln -s "$CHROMIUM_DIR" ~/chromium
  fi
  if [ -z "$CHROMIUM_DIR" ] ; then
    mkdir -p ~/chromium
  fi
fi
cd ~/chromium
if [ ! -f .gclient ] ; then
  ~/depot_tools/fetch android --nosvn=True || \
      echo "Ignore the error above if this is a first-time setup"
fi
if ! grep '"safesync_url":' .gclient ; then
  echo -n ''  # Noop
  # TODO(pwnall): do this right
  # echo '"safesync_url": "https://chromium-status.appspot.com/git-lkgr"' >> .gclient
fi
cd ~/chromium/src
sudo ./build/install-build-deps-android.sh
sudo ./build/install-build-deps.sh --no-syms --lib32 --arm --no-prompt
gclient runhooks

# Chromium build setup.
# https://code.google.com/p/chromium/wiki/LinuxBuildInstructions
# https://code.google.com/p/chromium/wiki/AndroidBuildInstructions

# Chromium build depedenecies not covered by the Chromium scripts.
sudo apt-get install -y ia32-libs libc6-dev-i386 g++-multilib
