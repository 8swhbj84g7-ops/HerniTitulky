/* SPDX-License-Identifier: AGPL-3.0-or-later */
(function () {
    "use strict";
    window.PlayCaptionReceiverUI = function (document) {
        var choices = document.getElementById("choices");
        var yellow = document.getElementById("choice-frame");
        var blue = document.getElementById("ocr-frame");
        var subtitle = document.getElementById("subtitle");
        var lastChoiceId = null;
        var lastFrameMode = 0;
        var choicesUntil = 0, yellowUntil = 0, blueUntil = 0, greenUntil = 0;

        function positionFrame(node, area) {
            node.style.left = (area[0] * 100) + "%";
            node.style.top = (area[1] * 100) + "%";
            node.style.width = ((area[2] - area[0]) * 100) + "%";
            node.style.height = ((area[3] - area[1]) * 100) + "%";
        }

        function positionChoices(area) {
            var leftRoom = area[0] - 0.02;
            var rightRoom = 1 - area[2] - 0.02;
            var width = Math.min(0.42, Math.max(leftRoom, rightRoom));
            var left = leftRoom >= rightRoom ? area[0] - width - 0.01 : area[2] + 0.01;
            var top = area[1];
            if (width < 0.2) {
                // Široký dokument nemá místo vedle; překlad zůstane v jeho zvolené oblasti.
                width = Math.min(0.8, Math.max(0.25, area[2] - area[0]));
                left = Math.min(area[0], 1 - width);
            }
            choices.style.left = (Math.max(0, left) * 100) + "%";
            choices.style.width = (width * 100) + "%";
            choices.style.maxHeight = "90vh";
            var viewport = document.documentElement.clientHeight || 1080;
            var height = Math.min(0.9, choices.scrollHeight / viewport || 0);
            choices.style.top = (Math.max(0.01, Math.min(top, 0.99 - height)) * 100) + "%";
        }

        this.handle = function (packet, now) {
            if (packet.kind === "ocr") {
                positionFrame(blue, packet.area);
                blue.hidden = !packet.visible;
                blueUntil = now + 8000;
                return true;
            }
            if (packet.kind !== "choices") return false;
            var changed = packet.id !== lastChoiceId;
            if (changed || packet.frame !== lastFrameMode) {
                positionFrame(yellow, packet.area);
                yellow.hidden = packet.frame === 0;
                yellowUntil = packet.frame === 2 ? Infinity : now + 8000;
                choicesUntil = packet.frame > 0 ? Infinity : now + (packet.selected === -2 ? 300000 : 45000);
            }
            lastFrameMode = packet.frame;
            if (!changed) return true;
            lastChoiceId = packet.id;
            choices.textContent = "";
            packet.items.forEach(function (text, index) {
                var row = document.createElement("div");
                row.textContent = text;
                row.className = "choice" + (index === packet.selected ? " selected" : "");
                choices.appendChild(row);
            });
            choices.hidden = packet.items.length === 0;
            positionChoices(packet.area);
            return true;
        };

        this.caption = function (packet, now) {
            subtitle.textContent = packet.text;
            if (packet.colors && packet.colors.some(function (color) { return color !== 0; })) {
                subtitle.textContent = "";
                packet.text.split("\n").forEach(function (line, index) {
                    if (index) subtitle.appendChild(document.createTextNode("\n"));
                    var colon = line.indexOf(":");
                    var color = packet.colors[index] || 0;
                    if (color && colon >= 1 && colon <= 25) {
                        var name = document.createElement("span");
                        name.textContent = line.slice(0, colon + 1);
                        var argb = color >>> 0;
                        name.style.color = "rgba(" + [(argb >>> 16) & 255, (argb >>> 8) & 255, argb & 255, (argb >>> 24) / 255].join(",") + ")";
                        subtitle.appendChild(name);
                        subtitle.appendChild(document.createTextNode(line.slice(colon + 1)));
                    } else subtitle.appendChild(document.createTextNode(line));
                });
            }
            subtitle.style.outline = packet.editing ? "4px dashed #78e89b" : "";
            greenUntil = now + 8000;
        };

        this.tick = function (now) {
            if (now >= choicesUntil) choices.hidden = true;
            if (now >= yellowUntil) yellow.hidden = true;
            if (now >= blueUntil) blue.hidden = true;
            if (now >= greenUntil) subtitle.style.outline = "";
        };

        this.clear = function () {
            choices.hidden = true; yellow.hidden = true; blue.hidden = true;
            choices.textContent = "";
            subtitle.style.outline = "";
            lastChoiceId = null; lastFrameMode = 0;
            choicesUntil = yellowUntil = blueUntil = greenUntil = 0;
        };
    };
}());
