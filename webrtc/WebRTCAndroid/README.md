[TOC]

# [project] WebRTCAndroid

this project is a demo show how webrtc run in android and 1to1 video with two devices.

# How to run:

## STUN/TURN Server

before run it, use a STUN/TURN server.

My choice is coTurn:

```bash
git clone https://github.com/coturn/coturn.git
cd coturn
sudo apt-get install build-essential pkg-config libssl-dev libssl-dev libsqlite3-dev libevent-dev libpq-dev mysql-client libmysqlclient-dev libhiredis-dev
git checkout 4.6.2
sudo mkdir /usr/local/coturn
./configure --prefix=/usr/local/coturn
sudo make install
cd /usr/local/coturn/etc
sudo cp turnserver.conf.default turnserver.conf
sudo vim turnserver.conf
```

modify below lines:

```config
listening-port=3478
external-ip=YOUR_STUN/TURN_SERVER_IP
user=USERNAME:PASSWORD
realm=THE_DOMAIN_WHICH_POINT_TO_THIS_EXTERNAL-IP
```

run coTurn:

```bash
/usr/local/coturn/bin/turnserver -c /usr/local/coturn/etc/turnserver.conf
```

## Configure in Android Project

edit file `WebRTCAndroid/app/src/main/res/values/strings.xml`, and fill in the above coTurn configuration:

```xml
    <string name="default_server">https://IP_OR_DOMAIN:PORT</string>
    <string name="STUN_SERVER">stun:IP_OR_DOMAIN:PORT</string>
    <string name="STUN_USRNAM">username</string>
    <string name="STUN_PASWOD">password</string>
```

## Signal Server

see `../WebRTCAndroidSignalServer/README.md`.

## Run/Test

With two android phone, or use two chrome, or use one chrome and one android phone.