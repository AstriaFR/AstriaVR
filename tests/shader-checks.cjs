// Desktop WebGL harness for the actual production projection shader.
// Only the Android external-texture sampler is replaced by a regular test texture.
const fs = require('node:fs');
const path = require('node:path');
const {chromium} = require('playwright');

(async () => {
  const source = fs.readFileSync(path.join(__dirname, '../app/src/main/java/dev/astriavr/player/VrView.kt'), 'utf8');
  const extract = name => source.match(new RegExp(`private const val ${name} = """([\\s\\S]*?)"""`))[1];
  const vertex = extract('VERTEX_SHADER');
  const fragment = extract('FRAGMENT_SHADER').replace(/#extension GL_OES_EGL_image_external : require/, '')
    .replace(/samplerExternalOES/g, 'sampler2D');
  const browser = await chromium.launch({headless: true, executablePath: process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe',
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader']});
  try {
    const page = await browser.newPage();
    const result = await page.evaluate(({vertex, fragment}) => {
      const canvas = document.createElement('canvas'); canvas.width = canvas.height = 1;
      const gl = canvas.getContext('webgl', {antialias:false, preserveDrawingBuffer:true});
      if (!gl) throw new Error('WebGL unavailable');
      function shader(type, text) {
        const s = gl.createShader(type); gl.shaderSource(s,text); gl.compileShader(s);
        if (!gl.getShaderParameter(s,gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
        return s;
      }
      const program=gl.createProgram(); gl.attachShader(program,shader(gl.VERTEX_SHADER,vertex));
      gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fragment)); gl.linkProgram(program);
      if (!gl.getProgramParameter(program,gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program));
      gl.useProgram(program);
      const pos=gl.getAttribLocation(program,'aPosition');
      gl.bindBuffer(gl.ARRAY_BUFFER,gl.createBuffer()); gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,1,-1,-1,1,1,1]),gl.STATIC_DRAW);
      gl.enableVertexAttribArray(pos); gl.vertexAttribPointer(pos,2,gl.FLOAT,false,0,0);
      const u=name=>gl.getUniformLocation(program,name);
      gl.uniformMatrix4fv(u('uTextureMatrix'),false,new Float32Array([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]));
      gl.uniform3f(u('uProjection'),1,Math.PI/4,0); gl.uniform3f(u('uPhoneWarp'),1,1,0);
      gl.uniform1f(u('uPhoneEllipse'),0); gl.uniform1f(u('uHasFrame'),1); gl.uniform1i(u('uVideo'),0);
      const normalize=v=>{const n=Math.hypot(...v);return v.map(x=>x/n);};
      const encode=(v,eye)=>[Math.round(20+70*(v[0]+1)),Math.round(20+70*(v[1]+1)),Math.round(20+50*(v[2]+1)+eye*100)];
      // Per-face bases: forward, right, down. Atlas tiles are listed top to bottom.
      const cube=[[[1,0,0],[0,0,1],[0,-1,0]], [[-1,0,0],[0,0,-1],[0,-1,0]],
        [[0,1,0],[1,0,0],[0,0,-1]], [[0,-1,0],[1,0,0],[0,0,1]],
        [[0,0,-1],[1,0,0],[0,-1,0]], [[0,0,1],[-1,0,0],[0,-1,0]]];
      const eac=[cube[1],cube[4],cube[0], [[0,-1,0],[0,0,1],[-1,0,0]],
        [[0,0,1],[0,1,0],[-1,0,0]], [[0,1,0],[0,0,-1],[-1,0,0]]];
      function texture(kind,span,layout,flipPoles) {
        const ew=kind===0?512:kind===1?256:kind===2?384:512;
        const eh=kind===0?256:kind===1?256:kind===2?256:288;
        const w=ew*(layout===0?2:1),h=eh*(layout===1?2:1), data=new Uint8Array(w*h*4);
        for(let y=0;y<h;y++)for(let x=0;x<w;x++) {
          const eye=layout===0?Math.floor(x/ew):layout===1?1-Math.floor(y/eh):0;
          const lu=(x%ew+.5)/ew,lv=(y%eh+.5)/eh;
          let v;
          if(kind===0) {
            const yaw=(lu-.5)*span,pitch=(lv-.5)*Math.PI;
            v=[Math.sin(yaw)*Math.cos(pitch),Math.sin(pitch),-Math.cos(yaw)*Math.cos(pitch)];
          } else if(kind===1) {
            const x=(lu-.5)*2,y=(lv-.5)*2,r=Math.hypot(x,y),angle=r*Math.PI/2;
            v=r>1?null:[Math.sin(angle)*x/Math.max(r,1e-9),Math.sin(angle)*y/Math.max(r,1e-9),-Math.cos(angle)];
          } else {
            const top=1-lv, row=Math.min(1,Math.floor(top*2));
            const x=kind===3?(lu-2/ew)/(1-4/ew)*3:lu*3;
            const col=Math.max(0,Math.min(2,Math.floor(x)));
            let a=(x-col)*2-1,b=kind===3?((top-row*.5-2/eh)/(.5-4/eh))*2-1:(top*2-row)*2-1;
            if(kind===3) {a=Math.tan(a*Math.PI/4);b=Math.tan(b*Math.PI/4);}
            const face=row*3+col,[n,r,d]=(kind===3?eac:cube)[face];
            // SV3D cbmp specifies forward at the top of the upper face, backward at the top of the lower face.
            const sign=kind===2&&flipPoles&&(face===2||face===3)?-1:1;
            v=normalize(n.map((k,i)=>k+sign*(a*r[i]+b*d[i])));
          }
          const rgb=v?encode(v,eye):[0,0,0],offset=(y*w+x)*4;
          data.set([...rgb,255],offset);
        }
        const texture=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,texture);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.LINEAR); gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.LINEAR);
        gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
        gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,w,h,0,gl.RGBA,gl.UNSIGNED_BYTE,data);
        gl.uniform2f(u('uVideoSize'),w,h);return texture;
      }
      let checks=0;const pixel=new Uint8Array(4), failures=[];
      for(let kind=0;kind<4;kind++)for(const flipPoles of (kind===2?[false,true]:[false]))for(const span of (kind===0?[Math.PI,Math.PI*2]:[Math.PI*2]))for(let layout=0;layout<3;layout++) {
        const tex=texture(kind,span,layout,flipPoles);
        gl.uniform1i(u('uSourceProjection'),kind);gl.uniform1f(u('uLongitudeSpan'),span);
        gl.uniform1f(u('uCubePolesFlipped'),flipPoles?1:0);
        for(const phone of [0,1])for(const eye of (layout===2?[0]:[0,1])) {
          gl.uniform1f(u('uPhoneMode'),phone);
          const rect=layout===0?[eye*.5,0,.5,1]:layout===1?[0,eye===0?.5:0,1,.5]:[0,0,1,1];
          gl.uniform4fv(u('uEyeRect'),rect);
          for(const yaw of [-179,-130,-89,-45,0,45,89,130,179])for(const pitch of [-75,-35,0,35,75]) {
            const y=yaw*Math.PI/180,p=pitch*Math.PI/180,d=[Math.sin(y)*Math.cos(p),Math.sin(p),-Math.cos(y)*Math.cos(p)];
            gl.uniformMatrix3fv(u('uPose'),false,[1,0,0,0,1,0,...d.map(v=>-v)]);
            gl.drawArrays(gl.TRIANGLE_STRIP,0,4);gl.readPixels(0,0,1,1,gl.RGBA,gl.UNSIGNED_BYTE,pixel);
            const hidden=kind===1?d[2]>0:kind===0&&Math.abs(y)>span/2;
            const expected=hidden?[0,0,0]:encode(d,eye);
            checks++;
            if(expected.some((value,i)=>Math.abs(value-pixel[i])>5)&&failures.length<10)
              failures.push({kind,flipPoles,layout,phone,eye,yaw,pitch,expected,actual:[...pixel]});
          }
        }
        gl.deleteTexture(tex);
      }
      if(failures.length) throw new Error(JSON.stringify(failures));
      return {checks,renderer:gl.getParameter(gl.RENDERER),error:gl.getError()};
    },{vertex,fragment});
    if(result.error!==0)throw new Error(`GL error ${result.error}`);
    console.log(`PASS: ${result.checks} production shader projection / hemisphere / stereo sampling checks (${result.renderer})`);
  } finally {await browser.close();}
})().catch(error=>{console.error(error);process.exitCode=1;});
