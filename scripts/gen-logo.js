// Generates Jolpai Keyboard's logo resources from the Jolpai Type mark (32x32 grid).
const fs = require('fs');
const path = require('path');
const ROOT = path.join(__dirname, '..');
const RES = path.join(ROOT, 'app-shell/src/main/res');

const BOARD = 'M7.2 9H24.8A5.2 5.2 0 0 1 30 14.2V23.8A5.2 5.2 0 0 1 24.8 29H7.2A5.2 5.2 0 0 1 2 23.8V14.2A5.2 5.2 0 0 1 7.2 9ZM6.2 12.6H7.4A1 1 0 0 1 8.4 13.6V14.8A1 1 0 0 1 7.4 15.8H6.2A1 1 0 0 1 5.2 14.8V13.6A1 1 0 0 1 6.2 12.6ZM10.8 12.6H12A1 1 0 0 1 13 13.6V14.8A1 1 0 0 1 12 15.8H10.8A1 1 0 0 1 9.8 14.8V13.6A1 1 0 0 1 10.8 12.6ZM15.4 12.6H16.6A1 1 0 0 1 17.6 13.6V14.8A1 1 0 0 1 16.6 15.8H15.4A1 1 0 0 1 14.4 14.8V13.6A1 1 0 0 1 15.4 12.6ZM20 12.6H21.2A1 1 0 0 1 22.2 13.6V14.8A1 1 0 0 1 21.2 15.8H20A1 1 0 0 1 19 14.8V13.6A1 1 0 0 1 20 12.6ZM24.6 12.6H25.8A1 1 0 0 1 26.8 13.6V14.8A1 1 0 0 1 25.8 15.8H24.6A1 1 0 0 1 23.6 14.8V13.6A1 1 0 0 1 24.6 12.6ZM6.2 17.4H7.4A1 1 0 0 1 8.4 18.4V19.6A1 1 0 0 1 7.4 20.6H6.2A1 1 0 0 1 5.2 19.6V18.4A1 1 0 0 1 6.2 17.4ZM10.8 17.4H12A1 1 0 0 1 13 18.4V19.6A1 1 0 0 1 12 20.6H10.8A1 1 0 0 1 9.8 19.6V18.4A1 1 0 0 1 10.8 17.4ZM20 17.4H21.2A1 1 0 0 1 22.2 18.4V19.6A1 1 0 0 1 21.2 20.6H20A1 1 0 0 1 19 19.6V18.4A1 1 0 0 1 20 17.4ZM24.6 17.4H25.8A1 1 0 0 1 26.8 18.4V19.6A1 1 0 0 1 25.8 20.6H24.6A1 1 0 0 1 23.6 19.6V18.4A1 1 0 0 1 24.6 17.4ZM10.6 22.2H21.4A1 1 0 0 1 22.4 23.2V24.2A1 1 0 0 1 21.4 25.2H10.6A1 1 0 0 1 9.6 24.2V23.2A1 1 0 0 1 10.6 22.2ZM13.6 19A2.4 2.4 0 1 0 18.4 19A2.4 2.4 0 1 0 13.6 19Z';
const OLIVE = 'M14.7 19A1.3 1.6 0 1 0 17.3 19A1.3 1.6 0 1 0 14.7 19Z';
const LEAF = 'M21.4 9.8C21.2 5 24.6 2 29.6 1.9C29.7 6.7 26.4 9.8 21.4 9.8Z';
// [x, y, w, h, order]: the key holes, lit one after another like typing
const TAPS = [[5.7, 13.1, 2.2, 2.2, 7], [10.3, 13.1, 2.2, 2.2, 0], [14.9, 13.1, 2.2, 2.2, 9], [19.5, 13.1, 2.2, 2.2, 2], [24.1, 13.1, 2.2, 2.2, 5], [5.7, 17.9, 2.2, 2.2, 4], [10.3, 17.9, 2.2, 2.2, 6], [19.5, 17.9, 2.2, 2.2, 1], [24.1, 17.9, 2.2, 2.2, 8], [10.1, 22.7, 11.8, 2, 3]];
const rr = (x, y, w, h, r = 0.6) => {
  const f = n => +n.toFixed(3);
  return `M${f(x + r)},${f(y)}H${f(x + w - r)}A${r},${r} 0 0 1 ${f(x + w)},${f(y + r)}V${f(y + h - r)}A${r},${r} 0 0 1 ${f(x + w - r)},${f(y + h)}H${f(x + r)}A${r},${r} 0 0 1 ${f(x)},${f(y + h - r)}V${f(y + r)}A${r},${r} 0 0 1 ${f(x + r)},${f(y)}Z`;
};

const TILE = '#A3B84A', INK = '#1E2118';
// 108dp adaptive canvas: the mark at 1.7x, centred (its box is x 2..30, y 1.9..29).
const S = 1.7, TX = +(54 - 16 * S).toFixed(3), TY = +(54 - 15.45 * S).toFixed(3);

const head = `<?xml version="1.0" encoding="utf-8"?>\n`;
const vec = (inner, extraNs = '') => `<vector xmlns:android="http://schemas.android.com/apk/res/android"${extraNs}
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
${inner}
</vector>`;

// --- launcher foreground and monochrome ---
const markGroup = (fill, withTaps) => `    <group
        android:translateX="${TX}"
        android:translateY="${TY}"
        android:scaleX="${S}"
        android:scaleY="${S}">
        <path android:fillColor="${fill}" android:fillType="evenOdd" android:pathData="${BOARD}" />
        <path android:fillColor="${fill}" android:pathData="${OLIVE}" />
        <path android:fillColor="${fill}" android:pathData="${LEAF}" />
    </group>`;
fs.writeFileSync(path.join(RES, 'drawable/ic_launcher_foreground.xml'), head +
`<!-- Jolpai's mark (shared with Jolpai Type): a keyboard with the keys cut out, an olive (জলপাই)
     as the lit key and a leaf. Dark ink on the olive ic_launcher_background, inside the
     adaptive-icon safe zone (66dp circle in the 108dp canvas). Made by scripts/gen-logo.js. -->
` + vec(markGroup(INK)) + '\n');
fs.writeFileSync(path.join(RES, 'drawable/ic_launcher_monochrome.xml'), head +
`<!-- Single-colour mark for Android 13+ themed icons; the launcher tints it. -->
` + vec(markGroup('#000000')) + '\n');

// --- animated splash icon (Android 12+), 1000 ms ---
const taps = TAPS.map(([x, y, w, h, k]) => `                <path android:name="tap${k}" android:fillColor="${INK}" android:fillAlpha="0" android:pathData="${rr(x, y, w, h)}" />`).join('\n');
const splashVec = `    <group android:name="mark" android:pivotX="54" android:pivotY="54" android:scaleX="0" android:scaleY="0">
        <group
            android:translateX="${TX}"
            android:translateY="${TY}"
            android:scaleX="${S}"
            android:scaleY="${S}">
            <path android:fillColor="${INK}" android:fillType="evenOdd" android:pathData="${BOARD}" />
            <group>
${taps}
            </group>
            <group android:name="olive" android:pivotX="16" android:pivotY="19">
                <path android:fillColor="${INK}" android:pathData="${OLIVE}" />
            </group>
            <group android:name="leaf" android:pivotX="21.4" android:pivotY="9.8" android:scaleX="0" android:scaleY="0" android:rotation="-40">
                <path android:fillColor="${INK}" android:pathData="${LEAF}" />
            </group>
        </group>
    </group>`;
const oa = (prop, from, to, start, dur, interp) =>
  `            <objectAnimator android:propertyName="${prop}" android:valueFrom="${from}" android:valueTo="${to}" android:startOffset="${start}" android:duration="${dur}" android:valueType="floatType"${interp ? ` android:interpolator="${interp}"` : ''} />`;
const target = (name, anims) => `    <target android:name="${name}">
        <aapt:attr name="android:animation">
            <set>
${anims.join('\n')}
            </set>
        </aapt:attr>
    </target>`;
const OVERSHOOT = '@android:anim/overshoot_interpolator', DECEL = '@android:interpolator/fast_out_slow_in';
const targets = [
  // the mark pops in
  target('mark', [oa('scaleX', 0, 1, 0, 420, OVERSHOOT), oa('scaleY', 0, 1, 0, 420, OVERSHOOT)]),
  // the leaf grows from the stem and waves into place
  target('leaf', [oa('scaleX', 0, 1, 220, 360, OVERSHOOT), oa('scaleY', 0, 1, 220, 360, OVERSHOOT),
    oa('rotation', -40, 12, 220, 360, DECEL), oa('rotation', 12, -6, 580, 200, DECEL), oa('rotation', -6, 0, 780, 200, DECEL)]),
  // keys tap in a typing rhythm
  ...TAPS.map(([, , , , k]) => target(`tap${k}`, [oa('fillAlpha', 0, 0.9, 300 + k * 50, 50), oa('fillAlpha', 0.9, 0, 350 + k * 50, 130)])),
  // and the olive key is pressed last, like Enter
  target('olive', [oa('scaleX', 1, 0.55, 760, 110, DECEL), oa('scaleY', 1, 0.55, 760, 110, DECEL),
    oa('scaleX', 0.55, 1, 870, 130, OVERSHOOT), oa('scaleY', 0.55, 1, 870, 130, OVERSHOOT)]),
];
fs.writeFileSync(path.join(RES, 'drawable/splash_logo.xml'), head +
`<!-- Android 12+ launch animation (1 s, see windowSplashScreenAnimatedIcon): the mark pops in, the
     leaf grows and waves, the keys tap like typing and the olive key is pressed last.
     Made by scripts/gen-logo.js. -->
<animated-vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt">
    <aapt:attr name="android:drawable">
${vec(splashVec).split('\n').map(l => '        ' + l).join('\n')}
    </aapt:attr>
${targets.join('\n')}
</animated-vector>
`);

// --- website: favicon / icon tiles ---
const svgMark = fill => `<g fill="${fill}"><path fill-rule="evenodd" d="${BOARD}"/><path d="${OLIVE}"/><path d="${LEAF}"/></g>`;
const tile = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32"><rect width="32" height="32" rx="7.5" fill="${TILE.toLowerCase()}"/><g transform="translate(4.8 5.2) scale(.7)">${svgMark(INK.toLowerCase())}</g></svg>\n`;
fs.writeFileSync(path.join(ROOT, 'website/images/logo.svg'), tile);
// full-bleed square for rasterising (the PNGs get rounded corners from the launcher / browser)
fs.writeFileSync(path.join(require('os').tmpdir(), 'jolpai-icon-square.svg'), tile.replace('rx="7.5"', 'rx="0"'));
fs.writeFileSync(path.join(require('os').tmpdir(), 'jolpai-icon-tile.svg'), tile);
console.log('ok', TX, TY);
