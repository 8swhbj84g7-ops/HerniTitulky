/* SPDX-License-Identifier: AGPL-3.0-or-later */
"use strict";
var Service = require("webos-service");
var dgram = require("dgram");
var os = require("os");
var crypto = require("crypto");
var parse = require("./protocol").parse;
var service = new Service(require("./package.json").name);
require("./switch-input").register(service);
var subscriptions = {};
var socket = null;
var announceTimer = null;
var ready = false;
var storyPeer = null;
var receiverName = "LG PlayCaption";
var sender = null;
var senderSeen = 0;
var rejectedAt = 0;

function notify(payload) {
    Object.keys(subscriptions).forEach(function (key) { subscriptions[key].respond(payload); });
}

function stop() {
    clearInterval(announceTimer);
    announceTimer = null;
    ready = false;
    storyPeer = null;
    sender = null;
    senderSeen = 0;
    rejectedAt = 0;
    if (socket) socket.close();
    socket = null;
}

function start() {
    if (socket) return;
    var receiver = dgram.createSocket("udp4");
    socket = receiver;
    var addresses = [];
    var identity = os.hostname();
    var interfaces = os.networkInterfaces();
    Object.keys(interfaces).forEach(function (name) {
        interfaces[name].forEach(function (iface) {
            if (iface.internal || iface.family !== "IPv4") return;
            identity += iface.mac;
            var ip = iface.address.split(".");
            var mask = iface.netmask.split(".");
            addresses.push(ip.map(function (part, i) { return Number(part) | (255 ^ Number(mask[i])); }).join("."));
        });
    });
    var id = crypto.createHash("sha256").update(identity).digest("hex").slice(0, 12);
    receiverName = "LG PlayCaption · " + id.toUpperCase();
    var announcement = Buffer.from("PLAYCAPTION_TV\tlg-" + id + "\t" + receiverName + "\t0", "utf8");
    function announce() {
        addresses.forEach(function (address) {
            receiver.send(announcement, 41988, address, function (error) {
                if (error) console.error("PlayCaption discovery:", error.code);
            });
        });
    }
    receiver.on("error", function (error) {
        notify({ returnValue: false, errorText: "Příjem UDP selhal: " + error.code });
        stop();
    });
    receiver.on("message", function (buffer, remote) {
        var packet = parse(buffer);
        var now = Date.now();
        if (!packet) return;
        // Starý Android mění UDP porty: do budoucího párování držíme zdroj podle IP.
        if (sender && sender !== remote.address && now - senderSeen < 5000) {
            if (!rejectedAt || now - rejectedAt >= 5000) {
                rejectedAt = now;
                notify({ returnValue: true, otherSender: true });
            }
            return;
        }
        if (packet.kind === "storyQuery") {
            receiver.send(Buffer.from("PCA1\t" + packet.id + "\tready"), remote.port, remote.address, function () {});
            return;
        }
        if (sender !== remote.address) storyPeer = null;
        sender = remote.address;
        senderSeen = now;
        if (packet.kind === "story") storyPeer = {id:packet.id,address:remote.address,port:remote.port};
        notify({ returnValue: true, packet: packet, source: remote.address });
    });
    receiver.bind(41989, "0.0.0.0", function () {
        receiver.setBroadcast(true);
        ready = true;
        notify({ returnValue: true, ready: true, name: receiverName });
        announce();
        announceTimer = setInterval(announce, 2000);
    });
}

service.register("listen", function (message) {
    if (!message.isSubscription) {
        message.respond({ returnValue: false, errorText: "Vyžadováno subscribe: true." });
        return;
    }
    subscriptions[message.uniqueToken] = message;
    message.respond({ returnValue: true, ready: ready, name: receiverName });
    start();
}, function (message) {
    delete subscriptions[message.uniqueToken];
    if (Object.keys(subscriptions).length === 0) stop();
});

service.register("storyAck", function (message) {
    var p = message.payload || {};
    if (!socket || !storyPeer || p.id !== storyPeer.id || ["reading", "done"].indexOf(p.state) < 0) {
        message.respond({returnValue:false}); return;
    }
    socket.send(Buffer.from("PCA1\t" + p.id + "\t" + p.state), storyPeer.port, storyPeer.address, function (error) {
        message.respond({returnValue:!error});
    });
});
