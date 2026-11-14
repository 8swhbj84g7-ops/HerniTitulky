/* SPDX-License-Identifier: AGPL-3.0-or-later */
(function () {
    "use strict";
    var status = document.getElementById("status");
    var subtitle = document.getElementById("subtitle");
    var hint = document.getElementById("hint");
    var bridge = null;
    var activityBridge = null;
    var activityWarning = "";
    var statusText = "";
    var lastActivity = 0;
    var timer = null;
    var lastSeen = 0;
    var lastId = null;
    var source = null;
    var subtitleUntil = 0;
    var hintUntil = 0;

    function showStatus(text) {
        statusText = text;
        status.textContent = activityWarning || text;
        status.hidden = false;
    }

    function clearText() {
        subtitle.hidden = true;
        hint.hidden = true;
        hint.textContent = "";
        lastId = null;
        source = null;
        lastSeen = 0;
    }

    function stop() {
        if (timer !== null) clearInterval(timer);
        timer = null;
        if (activityBridge) {
            activityBridge.onservicecallback = function () {};
            activityBridge.cancel();
            activityBridge = null;
        }
        if (bridge) {
            bridge.onservicecallback = function () {};
            bridge.cancel();
            bridge = null;
        }
        clearText();
        activityWarning = "";
    }

    function keepOverlayOpen() {
        lastActivity = Date.now();
        if (activityBridge) {
            activityBridge.onservicecallback = function () {};
            activityBridge.cancel();
        }
        var request = new PalmServiceBridge();
        activityBridge = request;
        request.onservicecallback = function (raw) {
            if (activityBridge !== request) return;
            var result;
            try { result = JSON.parse(raw); } catch (error) { result = {}; }
            activityWarning = result && result.returnValue === true ? "" :
                "PlayCaption · LG odmítá prodloužení překryvu, může se po 2 minutách zavřít.";
            status.textContent = activityWarning || statusText;
            status.hidden = !activityWarning && Boolean(lastSeen);
            activityBridge = null;
        };
        // LG zavírá nečinný overlay po 120 s, i když se jeho text průběžně mění.
        request.call("luna://com.webos.surfacemanager.screenSaver/restartUserActivityTimer", "{}");
    }

    function receive(raw) {
        var result;
        try { result = JSON.parse(raw); } catch (error) {
            clearText();
            showStatus("PlayCaption: neplatná odpověď přijímače. Zkus aplikaci spustit znovu.");
            return;
        }
        if (!result.returnValue) {
            clearText();
            showStatus("PlayCaption: " + (result.errorText || "přijímač není dostupný"));
            return;
        }
        var packet = result.packet;
        if (!packet) {
            showStatus(result.ready ? "PlayCaption · Na tabletu vyber LG PlayCaption · Zpět = zavřít" : "PlayCaption · Spouštím příjem…");
            return;
        }
        var now = Date.now();
        // Opakované pakety jsou heartbeat; nesmějí znovu zobrazit již prošlý titulek.
        var changed = packet.id !== lastId || source !== result.source || now - lastSeen > 5000;
        lastSeen = now;
        status.hidden = !activityWarning;
        if (!changed) return;
        lastId = packet.id;
        source = result.source;
        subtitle.textContent = packet.text;
        subtitle.hidden = !packet.text;
        subtitleUntil = now + packet.duration;
        var layout = packet.layout;
        var width = layout ? layout[2] : 0.8;
        var left = layout ? Math.max(width / 2, Math.min(1 - width / 2, layout[0])) : 0.5;
        subtitle.style.left = (left * 100) + "%";
        subtitle.style.bottom = ((1 - (layout ? layout[1] : 0.91)) * 100) + "%";
        subtitle.style.width = (width * 100) + "%";
        subtitle.style.fontSize = (38 * (layout ? layout[3] : 1)) + "px";
        if (packet.hint !== hint.textContent) {
            hint.textContent = packet.hint;
            hint.hidden = !packet.hint;
            hintUntil = now + 20000;
        }
    }

    function start() {
        stop();
        hint.textContent = "";
        showStatus("PlayCaption · Spouštím příjem…");
        if (typeof PalmServiceBridge === "undefined") {
            showStatus("PlayCaption: příjem je dostupný pouze na LG webOS.");
            return;
        }
        bridge = new PalmServiceBridge();
        bridge.onservicecallback = receive;
        bridge.call("luna://cz.playcaption.overlaytest.receiver/listen", JSON.stringify({ subscribe: true }));
        keepOverlayOpen();
        timer = setInterval(function () {
            var now = Date.now();
            if (now - lastActivity >= 45000) keepOverlayOpen();
            if (now >= subtitleUntil) subtitle.hidden = true;
            if (now >= hintUntil) hint.hidden = true;
            if (lastSeen && now - lastSeen > 5000) {
                clearText();
                hint.textContent = "";
                showStatus("PlayCaption · Spojení přerušeno, čekám na tablet · Zpět = zavřít");
            }
        }, 250);
    }

    // shortcut: overlay není veřejný kontrakt LG, ověřit na každém cílovém firmwaru.
    document.addEventListener("webOSRelaunch", start);
    document.addEventListener("keydown", function (event) {
        if (event.keyCode === 461 || event.key === "Escape") {
            event.preventDefault();
            stop();
            window.close();
        }
    });
    window.addEventListener("pagehide", stop);
    start();
}());
