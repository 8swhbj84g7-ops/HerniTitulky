/* SPDX-License-Identifier: AGPL-3.0-or-later */
"use strict";

function unescape(text) {
    return text.replace(/\\(.)/g, function (_, ch) { return ch === "n" ? "\n" : ch; });
}

exports.parse = function (buffer) {
    if (buffer.length > 8192) return null;
    var fields = buffer.toString("utf8").split("\t");
    if (fields[0] !== "PCT1" || fields.length < 5 || !/^\d{1,19}$/.test(fields[1])) return null;
    if (fields[3].length > 4096 || fields[4].length > 2048) return null;
    var duration = /^\d+$/.test(fields[2]) ? Number(fields[2]) : 6000;
    var layout = null;
    if (fields.length >= 9 && fields[5].indexOf("B:") !== 0) {
        var values = fields.slice(5, 9).map(function (value) { return value.trim() === "" ? NaN : Number(value); });
        if (values.every(Number.isFinite)) {
            layout = [
                Math.max(0, Math.min(1, values[0])),
                Math.max(0.05, Math.min(1, values[1])),
                Math.max(0.2, Math.min(1, values[2])),
                Math.max(0.5, Math.min(2.5, values[3]))
            ];
        }
    }
    return {
        id: fields[1],
        text: unescape(fields[3]),
        hint: unescape(fields[4]),
        duration: Math.min(60000, duration > 0 ? duration : 6000),
        layout: layout
    };
};
