# VM Setup Instructions

This library's repository includes some Chromium files that are painful to
build. Chromium's build process is a bit fussy, and the Android target is even
more fussy, so the least painful way of getting it done is to set up a VM with
the exact software that the build process was designed for.

This document contains step-by-step instructions for setting up the build VM
and building the files used in this library.


## VM Building

These are the manual steps for setting up a VM. They only need to be done once.

1. Get the 64-bit ISO for Ubuntu Server 12.10.
    * Go to http://releases.ubuntu.com/12.10/
    * Get the `64-bit PC (AMD64) server install image`

2. Set up a VirtualBox VM.
    * Name: ChromeWebView
    * Type: Linux
    * Version: Ubuntu 64-bit
    * RAM: 4096Mb
    * Disk: VDI, dynamic, 48Gb

3. Change the settings (Machine > Settings in the VirtualBox menu)
    * System > Processor > Processor(s): 4 (number of CPU cores on the machine)
    * Audio > uncheck Enable Audio
    * Network > Adapter 1 > Advanced > Adapter Type: virtio-net
    * Network > Adapter 2
        * check Enable network adapter
        * Attached to > Host-only Adapter
        * Advanced > Adapter Type: virtio-net
    * Ports > USB > uncheck Enable USB 2.0 (EHCI) Controller

4. Start VM and set up the server.
    * Select the Ubuntu ISO downloaded earlier.
    * Start a server installation, providing default answers, except:
        * Hostname: crbuild
        * Full name: crbuild
        * Username: crbuild
        * Password: crbuild
        * Confirm using a weak password
        * Encrypt home directory: no
        * Partitioning: Guided - use entire disk (no LVM or encryption)
        * Software to install: OpenSSH server

6. After the VM restarts, set up networking.
    * Log in using the VM console.
    * Open /etc/network/interfaces in a text editor (sudo vim ...)
    * Duplicate the "primary network interface" section
    * In the duplicate section, replace-all eth0 with eth1, primary with
      secondary
    * Save the file.
    * `sudo apt-get install -y avahi-daemon`
    * `sudo reboot`

7. Prepare to SSH into the VM.
    * If you don't have an ssh key
        * `ssh-keygen -t rsa`
        * press Enter all the way (default key type, no passphrase)

    ```bash
    ssh-copy-id crbuild@crbuild.local
    ssh crbuild@crbuild.local
    ```

8. Get the Oracle Java 6 JDK.
    * Go to http://www.oracle.com/technetwork/java/javase/downloads/index.html
    * Search for "Java SE 6", click on JDK
    * Click on the radio button for accepting the license
    * Download the Linux x86 non-RPM file (jdk-6uNN-linux-x64.bin)

    ```bash
    exit  # Get out of the VM ssh session, run this on the host.
    scp ~/Downloads/jdk-6u*-linux-x64.bin crbuild@crbuild.local:~/jdk6.bin
    ```

9. Set up the VM builds target platform(s). Choosing more than one platform
   will result in no incremental building being done.

    ```bash
    # ssh crbuild@crbuild.local
    touch ~/.build_arm
    touch ~/.build_x86
    ```

10. Optionally set the path of the directory that will hold the Chromium source
    code to. This is only used the first time the setup script runs, so it only
    needs to be set once.

    ```bash
    export CHROMIUM_DIR=/mnt/chromium
    ```


## VM Setup

The setup script will complete the work done above. The script is idempotent,
so it can be ran to bring a VM's software up to date.

```bash
# ssh crbuild@crbuild.local
curl -fLsS https://github.com/pwnall/chromeview/raw/master/crbuild/vm-setup.sh | sh
```

If the script fails, the steps in [vm-setup.sh](crbuild/vm-setup.sh) can be
copy-pasted one by one in the VM's ssh console. Please open an issue or pull
request if the script fails.

If this is the first time the Chromium source code is downloaded and
`$CHROMIUM_DIR` is defined, the directory at that path will be created and
symlinked into `~/chromium`. The source code can be moved around, as long as
the symlink is updated.


## Building

The build script will update the Chromium source code and do a build. For
infrequent builds, the setup script above should be ran before a build.

```bash
# ssh crbuild@crbuild.local
curl -fLsS https://github.com/pwnall/chromeview/raw/master/crbuild/vm-build.sh | sh
```
