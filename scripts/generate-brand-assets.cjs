// Original vector artwork. One geometry source emits Android vectors and reviewable SVGs.
const fs = require('fs');
const path = require('path');
const root = path.resolve(__dirname, '..');
const res = path.join(root, 'app/src/main/res');
const source = path.join(root, 'docs/design-assets');
fs.mkdirSync(source, { recursive: true });
const P = (d, stroke, width = 1, fill = 'none', alpha = 1) => ({ d, stroke, width, fill, alpha });
const circle = (x,y,r,stroke,width=1,fill='none',alpha=1) => P(`M ${x-r},${y} a ${r},${r} 0 1,0 ${r*2},0 a ${r},${r} 0 1,0 ${-r*2},0`,stroke,width,fill,alpha);
const star = (x,y,r,fill,alpha=1) => P(`M${x},${y-r} Q${x+.7},${y-.7} ${x+r},${y} Q${x+.7},${y+.7} ${x},${y+r} Q${x-.7},${y+.7} ${x-r},${y} Q${x-.7},${y-.7} ${x},${y-r}Z`,null,0,fill,alpha);
const svgPaths = layers => layers.map(p=>`<path d="${p.d}" fill="${p.fill}"${p.stroke?` stroke="${p.stroke}" stroke-width="${p.width}"`:''} opacity="${p.alpha}" stroke-linecap="round" stroke-linejoin="round"/>`).join('\n');
const xmlPaths = layers => layers.map(p=>`    <path android:pathData="${p.d}" android:fillColor="${p.fill==='none'?'#00000000':p.fill}" android:fillAlpha="${p.alpha}"${p.stroke?` android:strokeColor="${p.stroke}" android:strokeWidth="${p.width}" android:strokeAlpha="${p.alpha}" android:strokeLineCap="round" android:strokeLineJoin="round"`:''}/>`).join('\n');
function emit(name,w,h,layers,dpw=w,dph=h,folder='drawable') {
  const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${svgPaths(layers)}</svg>`;
  fs.writeFileSync(path.join(source,name+'.svg'),svg);
  if (!folder) return;
  fs.mkdirSync(path.join(res,folder),{recursive:true});
  fs.writeFileSync(path.join(res,folder,name+'.xml'),`<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="${dpw}dp" android:height="${dph}dp" android:viewportWidth="${w}" android:viewportHeight="${h}">\n${xmlPaths(layers)}\n</vector>\n`);
}
const ice='#EDF2FF', blue='#B4CCFF', violet='#B8AAE8';
// Broad layered veils echo the new app background. Keep contrast behind the small lettering.
const sky=[
  P('M0,0H108V108H0Z',null,0,'#080D25'),
  circle(94,20,64,null,0,'#263463',.42),
  circle(5,102,66,null,0,'#302D6D',.39),
  circle(56,53,49,null,0,'#253A73',.22),
  P('M-8,70 C20,24 52,63 116,20 L116,47 C70,84 27,44 -8,89Z',null,0,'#5361A8',.18),
  P('M-8,87 C23,48 57,94 116,37 L116,60 C71,101 34,61 -8,105Z',null,0,'#68549B',.17),
  P('M-4,38 C31,10 61,55 111,12 L111,24 C65,67 26,20 -4,54Z',null,0,'#617FBD',.11),
  P('M18,64 C14,34 37,17 72,18 M79,17 C103,26 104,53 90,72','#A6BEFF',.85,'none',.44),
  P('M12,88 C36,68 58,100 100,68',violet,.75,'none',.3),
];
const points=[[16,19],[30,12],[59,16],[82,17],[95,27],[12,44],[23,64],[88,65],[98,88],[74,93],[37,92],[14,82],[90,43],[42,22],[84,82],[29,80],[64,86],[98,56],[18,100]];
points.forEach(([x,y],i)=>sky.push(i%6===0?star(x,y,1.35,blue,.52):circle(x,y,i%3===0?.7:.42,null,0,ice,.31+i%4*.09)));
const emblem=[
  // Astria: only the initial is capitalized; the word stays above the VR symbol.
  P('M30,35 L34,25.5 L38,35 M31.5,31.8 H36.5',ice,1.4),
  P('M45.5,29.5 C43.3,28.4 39.6,29.1 39.6,31 C39.6,32.7 45.5,31.4 45.5,33.3 C45.5,35.3 41.9,35.6 39.5,34.5',ice,1.35),
  P('M49.5,26.5 V33 Q49.5,35.2 52.3,35 M47.5,29.5 H52.7',ice,1.35),
  P('M55,35 V29.2 M55,31.5 Q56.5,28.6 59,29.2',ice,1.35),
  P('M62,29.2 V35',ice,1.35),circle(62,26.5,.8,null,0,ice),
  P('M74.5,29.2 V35 M74.5,30.2 C71.4,28.2 68.8,30.1 68.8,32.2 C68.8,35 72.1,36.2 74.5,33.8',ice,1.35),
  P('M29,38 C44,36.3 64,36.3 78,38',blue,.55,'none',.6),
  // Asymmetric angular cuts make the VR monogram distinct from a standard font.
  P('M24,42 L32,40 L42,64 L51,40 L59,42 L46,76 L39,76Z',null,0,'#EEF3FF'),
  P('M24,42 L32,40 L42,64 L39,76Z',null,0,'#BBBFF3',.94),
  P('M50,43 L55,42 L44,71 L42,72Z',null,0,'#81C4FA',.96),
  P('M59,40 H74 Q88,40 87,51 Q87,58 77,62 L87,75 H76 L68,62 H64 L62,75 H53Z M65,48 V55 H73 Q79,55 79,51 Q79,48 73,48Z',null,0,'#D8EAFF'),
  P('M77,62 L87,75 H76 L68,62Z',null,0,'#A8A9F2',.91),
  P('M26,77 C40,84 65,82 84,73',blue,1.35,'none',.76),
  P('M31,79 C43,83 58,81 69,78',ice,.6,'none',.72),
  star(82,29,3.1,ice),star(23,56,1.2,violet,.9),circle(74,84,.75,null,0,ice,.72)
];
emit('astria_icon_background',108,108,sky);
emit('astria_icon_foreground',108,108,emblem);
emit('astria_icon_monochrome',108,108,emblem.map(p=>({...p,stroke:p.stroke?'#FFFFFF':null,fill:p.fill==='none'?'none':'#FFFFFF',alpha:p.alpha})));
emit('ic_launcher',108,108,[...sky,...emblem],108,108,'mipmap-anydpi');
for (const v of ['mipmap-anydpi-v26','mipmap-anydpi-v33']) {
  fs.mkdirSync(path.join(res,v),{recursive:true});
  fs.writeFileSync(path.join(res,v,'ic_launcher.xml'),`<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/astria_icon_background"/>\n    <foreground android:drawable="@drawable/astria_icon_foreground"/>\n${v.endsWith('33')?'    <monochrome android:drawable="@drawable/astria_icon_monochrome"/>\n':''}</adaptive-icon>\n`);
}
// The selected artwork is full-bleed: Android masks its image directly, without an inner square.
fs.writeFileSync(path.join(res,'mipmap-anydpi-v33/ic_launcher_compat.xml'),`<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n    <background android:drawable="@drawable/astria_launcher_art"/>\n    <foreground android:drawable="@android:color/transparent"/>\n</adaptive-icon>\n`);
// Custom lettering: AstriaVR. No font dependency, consistent contours on all phones.
const mark=[
 P('M3,24 C9,29 25,25 30,17 C33,10 24,8 17,12',blue,.8,'none',.55),star(17,18,10,ice),star(30,5,1.7,blue),circle(6,8,.8,null,0,blue,.8),
 P('M42,31 L52,7 L62,31 M46,22 L58,22',ice,2.05),
 P('M79,17 C76,13 66,13 66,19 C66,24 79,20 79,26 C79,32 69,34 65,29',ice,1.9),
 P('M89,9 V27 Q89,34 97,30 M84,16 H97',ice,1.9),
 P('M104,31 V16 M104,22 Q109,13 115,17',ice,1.9),
 P('M122,17 V31',ice,1.9),star(122,9,2.25,blue),
 P('M145,16 V31 M145,20 C139,11 130,15 130,24 C130,33 140,35 145,27',ice,1.9),
 P('M157,9 L165,31 L175,9',blue,2.05),
 P('M183,31 V9 H190 Q201,9 201,16 Q201,23 183,23 M192,23 L202,31',blue,2.05),
 P('M49,36 C87,38 141,36 183,35',blue,.55,'none',.32),star(207,9,2.8,ice,.9),circle(152,4,.65,null,0,violet,.8)
];
emit('astria_wordmark',216,42,mark,170,33);

// Pencil-like controller: light double contours, crosshatching and exact button geometry.
const shell='M79,65 C91,47 115,42 136,53 L153,60 H247 L264,53 C285,42 309,47 321,65 L344,106 C361,146 374,214 358,234 C344,251 320,227 292,190 L271,177 H129 L108,190 C80,227 56,251 42,234 C26,214 39,146 56,106Z';
const sketch=[
 P(shell,'#D1DAED',1.8,'#121B30'),
 P('M82,67 C94,50 115,45 136,56 L153,63 H246 M326,79 C350,127 369,212 354,230 M39,213 C36,187 47,134 59,109',blue,.65,'none',.4),
 P('M83,67 Q101,45 134,55 L145,64 L137,76 L86,82Z','#A8B8D2',1.1,'#1B263B'),
 P('M317,67 Q299,45 266,55 L255,64 L263,76 L314,82Z','#A8B8D2',1.1,'#1B263B'),
 P('M86,40 Q87,25 96,25 H119 Q129,25 130,40 L126,47 H90Z',blue,1.2,'#18243B'),
 P('M270,40 Q271,25 281,25 H304 Q313,25 314,40 L310,47 H274Z',blue,1.2,'#18243B'),
 circle(105,117,30,'#BACAE5',1.4,'#17233A'),circle(105,117,22,'#90A4C3',1.05),circle(105,117,17,'#798BA7',.6),
 circle(246,178,29,'#BACAE5',1.4,'#17233A'),circle(246,178,21,'#90A4C3',1.05),circle(246,178,16,'#798BA7',.6),
 P('M140,155 H157 V169 H171 V186 H157 V200 H140 V186 H126 V169 H140Z','#BDCAE1',1.4,'#1A2840'),
 P('M144,160 H153 M130,173 V182 M143,195 H153 M166,174 V182','#879CBF',.7),
 circle(200,89,12,'#A8B8D2',1.2),star(200,89,6,blue,.9),
 circle(180,125,8,'#A8B8D2',1),circle(220,125,8,blue,1.1),
 P('M176,122 H181 V126 H176Z M179,124 H184 V128 H179Z','#ADBBD5',.8),
 P('M217,122 H223 M217,125 H223 M217,128 H223',blue,.8),
 circle(297,95,13,'#D5CA9B',1.15,'#1D293E'),circle(273,119,13,'#94BDE1',1.15,'#1D293E'),circle(321,119,13,'#D6A4B1',1.15,'#1D293E'),circle(297,143,13,'#A9CDB9',1.15,'#1D293E')
];
// Hatching remains deliberately sparse so small guide illustrations stay clear.
for(let i=0;i<7;i++) { const y=153+i*8; sketch.push(P(`M${54-i*.35},${y} l${8+i*.8},-8`,'#7186AA',.65,'none',.6)); sketch.push(P(`M${346+i*.35},${y} l${-8-i*.8},-8`,'#7186AA',.65,'none',.6)); }
sketch.push(P('M69,201 Q66,223 58,225 M331,201 Q334,223 342,225','#7D94B8',.8,'none',.7));
const glyph={L:'M0,0V10H6',T:'M0,0H8 M4,0V10',R:'M0,10V0H4Q8,0 8,3Q8,6 0,6 M4,6L8,10',B:'M0,0V10H4Q9,10 8,7Q8,5 0,5 M0,0H4Q8,0 8,3Q8,5 0,5',A:'M0,10L4,0L8,10 M2,6H6',X:'M0,0L8,10 M8,0L0,10',Y:'M0,0L4,5L8,0 M4,5V10'};
function translated(d,x,y,scale=1) { // For these simple uppercase glyphs, transform numeric pairs by command arity.
 let axis=0,cmd=''; return d.replace(/[A-Za-z]|-?\d+(?:\.\d+)?/g,t=>{if(/[A-Za-z]/.test(t)){cmd=t;axis=0;return t;}const n=+t; if(cmd==='H') return +(n*scale+x).toFixed(2); if(cmd==='V') return +(n*scale+y).toFixed(2); const value=n*scale+(axis++%2===0?x:y);return +value.toFixed(2);});
}
function letters(text,x,y,color=ice,s=.8) { [...text].forEach((ch,i)=>sketch.push(P(translated(glyph[ch],x+i*10*s,y,s),color,1.05))); }
letters('LT',101,31,blue,.72);letters('RT',285,31,blue,.72);
letters('LB',104,60,ice,.75);letters('RB',283,60,ice,.75);
letters('Y',293,90,'#E5D4AA',1);letters('X',269,114,'#AED3F7',1);letters('B',317,114,'#EFBECC',1);letters('A',293,138,'#C3E6D1',1);
emit('controller_sketch',400,270,sketch,320,216);
// A muted dialog surface using the same stars, with generous calm areas for text.
const dialog=[P('M20,0H380Q400,0 400,20V260Q400,280 380,280H20Q0,280 0,260V20Q0,0 20,0Z','#4A6394',1,'#0C1935')];
[[30,18],[103,11],[367,41],[386,102],[15,190],[350,263],[241,270]].forEach(([x,y],i)=>dialog.push(circle(x,y,i%2?.5:.85,null,0,'#87D9FF',.22)));
emit('dialog_stars',400,280,dialog);
fs.writeFileSync(path.join(res,'drawable/dialog_panel.xml'),`<?xml version="1.0" encoding="utf-8"?>\n<layer-list xmlns:android="http://schemas.android.com/apk/res/android"><item android:drawable="@drawable/dialog_stars"/></layer-list>\n`);
async function previews(){
 const sharp=require('sharp'); const out=path.join(root,'app/build/icon-previews');fs.mkdirSync(out,{recursive:true});
 const launcherArtwork=path.join(source,'launcher-astria-4.1.png');
 await sharp(launcherArtwork).webp({quality:92,effort:6}).toFile(path.join(res,'drawable-nodpi/astria_launcher_art.webp'));
 fs.rmSync(path.join(res,'drawable-nodpi/astria_launcher_art.png'),{force:true});
 for (const [density,size] of [['mdpi',48],['hdpi',72],['xhdpi',96],['xxhdpi',144],['xxxhdpi',192]]) {
  const folder=path.join(res,`mipmap-${density}`);fs.mkdirSync(folder,{recursive:true});
  await sharp(launcherArtwork).resize(size,size).webp({lossless:true,effort:6}).toFile(path.join(folder,'ic_launcher_compat.webp'));
  fs.rmSync(path.join(folder,'ic_launcher_compat.png'),{force:true});
 }
 for(const [name,w] of [['ic_launcher',432],['astria_wordmark',864],['controller_sketch',1200]]) {
  const buffer=await (name==='ic_launcher'?sharp(launcherArtwork):sharp(path.join(source,name+'.svg'),{density:192})).resize({width:w}).png().toBuffer();
  await sharp({create:{width:w,height:name==='ic_launcher'?w:Math.round(w*(name==='astria_wordmark'?42/216:270/400)),channels:4,background:'#0D1527'}}).composite([{input:buffer}]).png().toFile(path.join(out,name+'-preview.png'));
 }
}
previews().catch(e=>{console.error(e);process.exitCode=1;});
