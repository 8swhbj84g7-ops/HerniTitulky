/* SPDX-License-Identifier: AGPL-3.0-or-later */
(function () {
    "use strict";
    var status = document.getElementById("status");
    var subtitle = document.getElementById("subtitle");
    var hint = document.getElementById("hint");
    var receiverUI = window.PlayCaptionReceiverUI ? new window.PlayCaptionReceiverUI(document) : null;
    var inputPanel = document.getElementById("inputs");
    var inputBridge = null;
    var inputButtons = [];
    var inputIndex = 0;
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

    function configureLaunch(params) {
        if (!inputPanel) return;
        var detail = params;
        if (!detail) {
            try { detail = JSON.parse(window.PalmSystem && window.PalmSystem.launchParams || "{}"); }
            catch (error) { detail = {}; }
        }
        inputPanel.hidden = detail && detail.resumeOverlay === true;
        if (inputPanel.hidden) return;
        var options = document.getElementById("input-options");
        var inputStatus = document.getElementById("input-status");
        var current = document.getElementById("input-current");
        options.textContent = "";
        inputButtons = [];
        inputStatus.textContent = "Načítám HDMI vstupy…";
        current.disabled = false;
        current.onclick = function () { inputPanel.hidden = true; };
        if (inputBridge) inputBridge.cancel();
        var request = new PalmServiceBridge();
        inputBridge = request;
        request.onservicecallback = function (raw) {
            if (inputBridge !== request) return;
            var result;
            try { result = JSON.parse(raw); } catch (error) { result = {}; }
            if (!result || typeof result !== "object") result = {};
            inputBridge = null;
            var devices = Array.isArray(result.devices) ? result.devices.filter(function (device) {
                return device && /^com\.webos\.app\.hdmi[1-9][0-9]*$/.test(device.appId || "");
            }) : [];
            if (!result.returnValue || !Array.isArray(result.devices)) {
                inputStatus.textContent = "LG neposkytla seznam vstupů. Nech TV na HDMI a spusť přijímač z počítače.";
            } else {
                inputStatus.textContent = "";
                devices.forEach(function (device) {
                        var button = document.createElement("button");
                        button.textContent = "HDMI " + device.port + " · " + String(device.label || "").slice(0, 100);
                        button.onclick = function () {
                            inputStatus.textContent = "Přepínám vstup a spouštím titulky…";
                            inputButtons.forEach(function (item) { item.disabled = true; });
                            try { localStorage.setItem("playcaption-input", device.appId); } catch (error) {}
                            var switcher = new PalmServiceBridge();
                            inputBridge = switcher;
                            switcher.onservicecallback = function (answer) {
                                if (inputBridge !== switcher) return;
                                inputBridge = null;
                                var response;
                                try { response = JSON.parse(answer); } catch (error) { response = {}; }
                                if (!response || typeof response !== "object") response = {};
                                if (response.returnValue) inputPanel.hidden = true;
                                else {
                                    inputStatus.textContent = response.errorText || "Přepnutí se nepodařilo.";
                                    inputButtons.forEach(function (item) { item.disabled = false; });
                                }
                            };
                            switcher.call("luna://cz.playcaption.overlaytest.receiver/switchInput", JSON.stringify({ id: device.appId }));
                        };
                        options.appendChild(button);
                        inputButtons.push(button);
                    });
            }
            inputButtons.push(current);
            var saved;
            try { saved = localStorage.getItem("playcaption-input"); } catch (error) {}
            inputIndex = Math.max(0, devices.findIndex(function (device) { return device.appId === saved; }));
            inputButtons[inputIndex].focus();
        };
        request.call("luna://com.webos.service.eim/getAllInputStatus", "{}");
    }

    function showStatus(text) {
        statusText = text;
        status.textContent = activityWarning || text;
        status.hidden = false;
    }

    function clearText(preserveStory) {
        if (receiverUI) {
            if (preserveStory && receiverUI.storyActive()) receiverUI.pauseStory(Date.now()); else receiverUI.clear();
        }
        subtitle.hidden = true;
        hint.hidden = true;
        hint.textContent = "";
        lastId = null;
        source = null;
        lastSeen = 0;
    }

    function stop() {
        if (inputBridge) {
            inputBridge.onservicecallback = function () {};
            inputBridge.cancel();
            inputBridge = null;
        }
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

    var ackBridge = null;
    function storyAck(id, state) {
        if (ackBridge) return;
        var request = new PalmServiceBridge(); ackBridge = request;
        var timeout = setTimeout(function () { if (ackBridge === request) { ackBridge = null; request.cancel(); } }, 2000);
        request.onservicecallback = function () { clearTimeout(timeout); if (ackBridge === request) ackBridge = null; };
        request.call("luna://cz.playcaption.overlaytest.receiver/storyAck", JSON.stringify({id:id,state:state}));
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
        var connectionChanged = source !== result.source || (lastSeen && now - lastSeen > 5000);
        if (connectionChanged) clearText(packet.kind === "story" && (!source || source === result.source));
        if (receiverUI && receiverUI.handle(packet, now)) {
            lastSeen = now;
            source = result.source;
            status.hidden = !activityWarning;
            return;
        }
        if (packet.kind !== "story" && receiverUI && receiverUI.storyActive()) return;
        if (packet.kind === "story" && receiverUI && receiverUI.oldStory(packet.id)) return;
        // Opakované pakety jsou heartbeat; nesmějí znovu zobrazit již prošlý titulek.
        var changed = packet.id !== lastId || connectionChanged;
        lastSeen = now;
        status.hidden = !activityWarning;
        if (!changed && packet.kind !== "story") return;
        lastId = packet.id;
        source = result.source;
        if (!receiverUI) subtitle.textContent = packet.text;
        subtitle.hidden = !packet.text;
        subtitleUntil = now + packet.duration;
        var layout = packet.layout;
        var width = layout ? layout[2] : 0.8;
        var left = layout ? Math.max(width / 2, Math.min(1 - width / 2, layout[0])) : 0.5;
        subtitle.style.left = (left * 100) + "%";
        subtitle.style.bottom = ((1 - (layout ? layout[1] : 0.91)) * 100) + "%";
        subtitle.style.width = (width * 100) + "%";
        var viewport = document.documentElement ? document.documentElement.clientHeight : 1080;
        subtitle.style.fontSize = Math.min(38 * (layout ? layout[3] : 1), (viewport * 0.18 - 32) / 2.7) + "px";
        if (receiverUI && packet.kind === "story") {
            subtitleUntil = Infinity;
            var state = receiverUI.story(packet, now);
            if (state) storyAck(packet.id, state);
        } else if (receiverUI) receiverUI.caption(packet, now);
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
            if (receiverUI) receiverUI.tick(now);
            if (now - lastActivity >= 45000) keepOverlayOpen();
            if (now >= subtitleUntil) subtitle.hidden = true;
            if (now >= hintUntil) hint.hidden = true;
            if (lastSeen && now - lastSeen > 5000) {
                clearText(true);
                hint.textContent = "";
                showStatus("PlayCaption · Spojení přerušeno, čekám na tablet · Zpět = zavřít");
            }
        }, 250);
    }

    // shortcut: overlay není veřejný kontrakt LG, ověřit na každém cílovém firmwaru.
    document.addEventListener("webOSRelaunch", function (event) { start(); configureLaunch(event && event.detail); });
    document.addEventListener("keydown", function (event) {
        if (inputPanel && !inputPanel.hidden && inputButtons.length) {
            if (event.keyCode >= 37 && event.keyCode <= 40) {
                event.preventDefault();
                inputIndex = (inputIndex + (event.keyCode === 37 || event.keyCode === 38 ? -1 : 1) + inputButtons.length) % inputButtons.length;
                inputButtons[inputIndex].focus();
                return;
            }
        }
        if (event.keyCode === 461 || event.key === "Escape") {
            event.preventDefault();
            stop();
            window.close();
        }
    });
    window.addEventListener("pagehide", stop);
    start();
    configureLaunch();
}());
