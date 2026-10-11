package com.example.service

import com.example.data.model.ExtractedInfo
import com.example.data.model.GeneratedIdentity
import org.json.JSONArray
import org.json.JSONObject

object AutomationScriptBuilder {

    /** نسخة الدرع — يرفعها المطور مع كل تقوية؛ المنسق يكشف الدرع القديم ويحذر */
    const val SHIELD_VERSION: Int = 11

    fun buildAntiDetectionScript(
        proxyIp: String = "",
        webrtcMode: String = "spoof", // "spoof", "disabled", "real"
        timezone: String = "America/New_York",
        language: String = "en-US",
        latitude: Double = 40.7128,
        longitude: Double = -74.0060,
        userAgent: String = "",
        fingerprintSeed: Long = 0L // Expert: stable per-session seed; 0 = legacy random
    ): String {
        val cleanIp = proxyIp.trim().ifBlank { "104.28.19.42" }
        val cleanTz = timezone.trim().ifBlank { "America/New_York" }
        val cleanLang = language.trim().ifBlank { "en-US" }
        val cleanUa = userAgent.trim().replace("'", "\\'")

        val uaLower = userAgent.lowercase()
        val isAndroid = uaLower.contains("android")
        val isIphone = uaLower.contains("iphone") || uaLower.contains("ipad")
        val isMac = !isIphone && uaLower.contains("macintosh")
        val isLinux = !isAndroid && uaLower.contains("linux")

        val platformString = when {
            isAndroid -> "Linux armv8l"
            isIphone -> "iPhone"
            isMac -> "MacIntel"
            isLinux -> "Linux x86_64"
            else -> "Win32"
        }

        val isMobile = isAndroid || isIphone
        val touchPoints = if (isMobile) 5 else 0
        val webglVendor = if (isIphone || isMac) "Apple Inc." else "Google Inc. (NVIDIA)"
        val webglRenderer = when {
            isAndroid -> "Adreno (TM) 740"
            isIphone -> "Apple GPU"
            isMac -> "Apple M2 Pro"
            else -> "ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 Direct3D11 vs_5_0 ps_5_0, D3D11)"
        }
        val clientPlatform = when {
            isAndroid -> "Android"
            isIphone -> "iOS"
            isMac -> "macOS"
            isLinux -> "Linux"
            else -> "Windows"
        }

        // Seeded PRNG so the SAME session keeps the SAME fingerprint (mid-session drift = bot signal)
        val seedInit = if (fingerprintSeed == 0L) "2166136261" else "${fingerprintSeed}>>>0"

        return """
(function() {
  var __cpaSeed = (window.__cpaSessionSeed || ($seedInit)) >>> 0;
  try {
    Object.defineProperty(window, '__cpaSessionSeed', { value: __cpaSeed, enumerable: false, writable: false, configurable: true });
  } catch(e) {}
  function __cpaHash(n) {
    var x = ((n ^ __cpaSeed) + 0x9e3779b9) >>> 0;
    x = Math.imul(x ^ (x >>> 16), 0x85ebca6b) >>> 0;
    x = Math.imul(x ^ (x >>> 13), 0xc2b2ae35) >>> 0;
    return (x ^ (x >>> 16)) >>> 0;
  }

  // 0. Native Function.prototype.toString & Global Property Enumeration Cloak
  var __cpaMaskNative = function(fn, name) { return fn; };
  try {
    if (!window.__cpaToStringMap && typeof WeakMap === 'function') {
      var tsMap = new WeakMap();
      Object.defineProperty(window, '__cpaToStringMap', { value: tsMap, enumerable: false, writable: false, configurable: true });
      var origFnToString = Function.prototype.toString;
      var cloakedObj = {
        toString: function() {
          try {
            if (tsMap.has(this)) return tsMap.get(this);
          } catch(e) {}
          try {
            return origFnToString.apply(this, arguments);
          } catch(err) {
            if (err && err.stack) {
              try { err.stack = err.stack.replace(/\n\s*at\s+[^\n]*(toString|<anonymous>)[^\n]*/g, ''); } catch(e2) {}
            }
            throw err;
          }
        }
      };
      tsMap.set(cloakedObj.toString, 'function toString() { [native code] }');
      Function.prototype.toString = cloakedObj.toString;
    }
    if (window.__cpaToStringMap) {
      __cpaMaskNative = function(fn, name) {
        try {
          if (typeof fn === 'function') {
            var fnName = (name !== undefined) ? name : (fn.name || '');
            try { Object.defineProperty(fn, 'name', { value: fnName, configurable: true }); } catch(e) {}
            window.__cpaToStringMap.set(fn, 'function ' + fnName + '() { [native code] }');
          }
        } catch(e) {}
        return fn;
      };
    }

    // Hide AndroidBridge and internal __cpa / _cpa / webdriver properties from Object.keys / getOwnPropertyNames / Reflect.ownKeys
    if (!window.__cpaPropFilterInstalled) {
      Object.defineProperty(window, '__cpaPropFilterInstalled', { value: true, enumerable: false, configurable: true });
      try {
        if (window.AndroidBridge) {
          var br = window.AndroidBridge;
          Object.defineProperty(window, 'AndroidBridge', { value: br, enumerable: false, configurable: true, writable: false });
        }
      } catch(e) {}
      var isHiddenWindowKey = function(k) {
        if (typeof k !== 'string') return false;
        return k === 'AndroidBridge' ||
               k.indexOf('__cpa') === 0 ||
               k.indexOf('_cpa') === 0 ||
               k.indexOf('cdc_') === 0 ||
               k.indexOf('${'$'}cdc_') === 0 ||
               k.indexOf('__webdriver') === 0 ||
               k.indexOf('__selenium') === 0 ||
               k.indexOf('__fxdriver') === 0 ||
               k.indexOf('domAutomation') === 0 ||
               k === '_phantom' ||
               k === 'callPhantom';
      };
      var origKeys = Object.keys;
      Object.keys = __cpaMaskNative(function(obj) {
        var res = origKeys.apply(this, arguments);
        if (obj === window) return res.filter(function(k) { return !isHiddenWindowKey(k); });
        return res;
      }, 'keys');
      var origGetOwnPropNames = Object.getOwnPropertyNames;
      Object.getOwnPropertyNames = __cpaMaskNative(function(obj) {
        var res = origGetOwnPropNames.apply(this, arguments);
        if (obj === window) return res.filter(function(k) { return !isHiddenWindowKey(k); });
        return res;
      }, 'getOwnPropertyNames');
      if (typeof Reflect !== 'undefined' && Reflect.ownKeys) {
        var origOwnKeys = Reflect.ownKeys;
        Reflect.ownKeys = __cpaMaskNative(function(obj) {
          var res = origOwnKeys.apply(this, arguments);
          if (obj === window) return res.filter(function(k) { return !isHiddenWindowKey(k); });
          return res;
        }, 'ownKeys');
      }
    }
  } catch(e) {}

  function defineProtoGetter(proto, inst, prop, getterFn) {
    var masked = __cpaMaskNative(getterFn, 'get ' + prop);
    try {
      if (inst && Object.prototype.hasOwnProperty.call(inst, prop)) {
        try { delete inst[prop]; } catch(e) {}
      }
      if (proto) {
        Object.defineProperty(proto, prop, { get: masked, enumerable: true, configurable: true });
        return;
      }
    } catch(e) {}
    try {
      if (inst) Object.defineProperty(inst, prop, { get: masked, enumerable: true, configurable: true });
    } catch(e2) {}
  }

  // Runtime UA synchronization so navigator.platform / WebGL / ClientHints NEVER contradict navigator.userAgent
  var __rawUa = ('$cleanUa' || (typeof navigator !== 'undefined' ? navigator.userAgent : '') || '').replace(/; wv\)/g, ')').replace(/Version\/\d+\.\d+\s+/g, '');
  var __runtimeUa = __rawUa.toLowerCase();
  var __isAndroid = __runtimeUa.indexOf('android') !== -1;
  var __isIphone = __runtimeUa.indexOf('iphone') !== -1 || __runtimeUa.indexOf('ipad') !== -1;
  var __isMac = !__isIphone && __runtimeUa.indexOf('macintosh') !== -1;
  var __isLinux = !__isAndroid && __runtimeUa.indexOf('linux') !== -1;
  var __isMobile = __isAndroid || __isIphone || __runtimeUa.indexOf('mobile') !== -1;
  var __isFirefox = __runtimeUa.indexOf('firefox/') !== -1;
  var __isSafariOnly = (__isIphone || __isMac) && __runtimeUa.indexOf('chrome/') === -1 && __runtimeUa.indexOf('crios/') === -1;
  var __platformStr = __isAndroid ? 'Linux armv8l' : (__isIphone ? 'iPhone' : (__isMac ? 'MacIntel' : (__isLinux ? 'Linux x86_64' : 'Win32')));
  var __clientPlat = __isAndroid ? 'Android' : (__isIphone ? 'iOS' : (__isMac ? 'macOS' : (__isLinux ? 'Linux' : 'Windows')));
  var __touchPts = __isMobile ? 5 : 0;

  // Seeded Real-Device Profile: ONE seed drives ALL signals so they agree like a real device
  // (fixed 8 cores / same GPU every session = correlatable bot signal)
  var __hwPick = function(arr, salt) { return arr[__cpaHash(salt >>> 0) % arr.length]; };
  var __hwCores = __isMobile ? __hwPick([4, 6, 8, 8], 101) : __hwPick([4, 6, 8, 8, 12, 16], 101);
  // deviceMemory spec enum caps at 8 (0.25/0.5/1/2/4/8) — 6 or 16 would be impossible values
  var __hwMem = __isMobile ? __hwPick([4, 4, 8], 102) : __hwPick([4, 8, 8], 102);
  var __androidGpuPairs = [
    ['Qualcomm', 'Adreno (TM) 740'], ['Qualcomm', 'Adreno (TM) 730'],
    ['Qualcomm', 'Adreno (TM) 660'], ['ARM', 'Mali-G715 MC7'],
    ['ARM', 'Mali-G710 MC10'], ['Samsung', 'Xclipse 920']
  ];
  var __winGpuPairs = [
    ['Google Inc. (NVIDIA)', 'ANGLE (NVIDIA, NVIDIA GeForce RTX 3060 Direct3D11 vs_5_0 ps_5_0, D3D11)'],
    ['Google Inc. (NVIDIA)', 'ANGLE (NVIDIA, NVIDIA GeForce GTX 1660 SUPER Direct3D11 vs_5_0 ps_5_0, D3D11)'],
    ['Google Inc. (NVIDIA)', 'ANGLE (NVIDIA, NVIDIA GeForce RTX 4060 Direct3D11 vs_5_0 ps_5_0, D3D11)'],
    ['Google Inc. (Intel)', 'ANGLE (Intel, Intel(R) UHD Graphics 770 Direct3D11 vs_5_0 ps_5_0, D3D11)'],
    ['Google Inc. (AMD)', 'ANGLE (AMD, AMD Radeon RX 6600 Direct3D11 vs_5_0 ps_5_0, D3D11)']
  ];
  var __macGpu = ['Apple M1 Pro', 'Apple M2', 'Apple M2 Pro', 'Apple M3'];
  var __gpuPair;
  if (__isAndroid) __gpuPair = __hwPick(__androidGpuPairs, 103);
  else if (__isIphone) __gpuPair = ['Apple Inc.', 'Apple GPU'];
  else if (__isMac) __gpuPair = ['Apple Inc.', __hwPick(__macGpu, 103)];
  else if (__isLinux) __gpuPair = ['Google Inc. (AMD)', 'ANGLE (AMD, AMD Radeon RX 6600 Direct3D11 vs_5_0 ps_5_0, D3D11)'];
  else __gpuPair = __hwPick(__winGpuPairs, 103);
  var __glVendor = __gpuPair[0];
  var __glRenderer = __gpuPair[1];

  // Seeded screen: real-device resolutions consistent with the UA class
  var __deskScreens = [[1920, 1080, 1], [1920, 1080, 1], [1366, 768, 1], [1536, 864, 1.25], [1440, 900, 1], [1600, 900, 1], [2560, 1440, 1]];
  var __andScreens = [[412, 915, 2.625], [390, 844, 2.5], [360, 800, 2], [384, 854, 2.25], [412, 892, 2.625]];
  var __iosScreens = [[390, 844, 3], [393, 852, 3], [428, 926, 3]];
  var __scr = __isAndroid ? __hwPick(__andScreens, 104) : (__isIphone ? __hwPick(__iosScreens, 104) : (__isMac ? [1512, 982, 2] : __hwPick(__deskScreens, 104)));
  var __scrW = __scr[0], __scrH = __scr[1], __scrDpr = __scr[2];
  var __scrAvailH = __isMobile ? __scrH : (__scrH - 40);

  // Seeded network profile (rtt/downlink jitter like a real 4G/broadband line)
  var __netRtt = 40 + (__cpaHash(105) % 55);
  var __netDl = 8 + (__cpaHash(106) % 9);

  // platformVersion derived from the UA itself (Android major) instead of a fixed guess
  var __andVerMatch = __runtimeUa.match(/android\s+(\d+)/);
  var __andPlatVer = __andVerMatch ? (__andVerMatch[1] + '.0.0') : '14.0.0';
  var __deskPlatVer = __isMac ? ('14.' + (__cpaHash(107) % 6) + '.0') : '15.0.0';
  var __platVer = __isAndroid ? __andPlatVer : __deskPlatVer;

  var __chromeFullMatch = __rawUa.match(/(?:Chrome|CriOS)\/(\d+\.\d+\.\d+\.\d+)/);
  var __chromeFullVer = __chromeFullMatch ? __chromeFullMatch[1] : '126.0.6478.122';
  var __chromeMajorVer = __chromeFullVer.split('.')[0] || '126';
  var __androidModelMatch = __rawUa.match(/Android\s+[\d.]+;\s*([^;)]+)\)/i);
  var __deviceModel = __isAndroid ? (__androidModelMatch ? __androidModelMatch[1].trim() : 'Pixel 8 Pro') : (__isIphone ? 'iPhone' : '');

  // 1. WebGL Vendor & Renderer spoofing (Idempotent & Native-Cloaked)
  try {
    function patchWebGlProto(proto) {
      if (!proto || proto.__cpaGlPatched) return;
      Object.defineProperty(proto, '__cpaGlPatched', { value: true, enumerable: false, configurable: true });
      var origGetParam = proto.getParameter;
      proto.getParameter = __cpaMaskNative(function(parameter) {
        if (parameter === 37445) return __glVendor;
        if (parameter === 37446) return __glRenderer;
        if (parameter === 7936) return 'WebKit';
        if (parameter === 7937) return 'WebKit WebGL';
        return origGetParam.apply(this, arguments);
      }, 'getParameter');

      if (proto.readPixels) {
        var origReadPixels = proto.readPixels;
        proto.readPixels = __cpaMaskNative(function(x, y, width, height, format, type, pixels) {
          origReadPixels.apply(this, arguments);
          try {
            if (pixels && pixels.length > 16) {
              var idx = ((__cpaSeed % 7) + 1) * 4;
              if (idx < pixels.length) {
                var delta = (__cpaSeed & 1) ? 1 : -1;
                pixels[idx] = Math.min(255, Math.max(0, pixels[idx] + delta));
              }
            }
          } catch(e) {}
        }, 'readPixels');
      }

      // WebGL extension list must match the spoofed GPU family (real Adreno exts + RTX name = exposed)
      var __extDesktop = ['ANGLE_instanced_arrays', 'EXT_blend_minmax', 'EXT_color_buffer_float', 'EXT_frag_depth', 'EXT_shader_texture_lod', 'EXT_sRGB', 'EXT_texture_filter_anisotropic', 'OES_element_index_uint', 'OES_standard_derivatives', 'OES_texture_float', 'OES_texture_float_linear', 'OES_texture_half_float', 'OES_texture_half_float_linear', 'OES_vertex_array_object', 'WEBGL_color_buffer_float', 'WEBGL_compressed_texture_s3tc', 'WEBGL_debug_renderer_info', 'WEBGL_debug_shaders', 'WEBGL_depth_texture', 'WEBGL_draw_buffers', 'WEBGL_lose_context'];
      var __extMobile = ['ANGLE_instanced_arrays', 'EXT_blend_minmax', 'EXT_color_buffer_float', 'EXT_frag_depth', 'EXT_shader_texture_lod', 'EXT_sRGB', 'EXT_texture_filter_anisotropic', 'OES_element_index_uint', 'OES_standard_derivatives', 'OES_texture_float', 'OES_texture_half_float', 'OES_texture_half_float_linear', 'OES_vertex_array_object', 'WEBGL_compressed_texture_etc', 'WEBGL_compressed_texture_astc', 'WEBGL_debug_renderer_info', 'WEBGL_debug_shaders', 'WEBGL_depth_texture', 'WEBGL_draw_buffers', 'WEBGL_lose_context'];
      var __extAllow = (__isMobile ? __extMobile : __extDesktop);
      try {
        proto.getSupportedExtensions = __cpaMaskNative(function() { return __extAllow.slice(); }, 'getSupportedExtensions');
        var origGetExt = proto.getExtension;
        if (typeof origGetExt === 'function') {
          proto.getExtension = __cpaMaskNative(function(name) {
            for (var ei = 0; ei < __extAllow.length; ei++) { if (__extAllow[ei] === name) return origGetExt.call(this, name); }
            return null;
          }, 'getExtension');
        }
      } catch(e) {}
    }
    if (window.WebGLRenderingContext) patchWebGlProto(WebGLRenderingContext.prototype);
    if (window.WebGL2RenderingContext) patchWebGlProto(WebGL2RenderingContext.prototype);
  } catch(e) {}

  // 2. Prototype-Accurate Hardware, Navigator, Screen & Client Hints
  try {
    var navProto = (typeof Navigator !== 'undefined' && Navigator.prototype) ? Navigator.prototype : null;
    defineProtoGetter(navProto, navigator, 'webdriver', function() { return false; });
    defineProtoGetter(navProto, navigator, 'platform', function() { return __platformStr; });
    defineProtoGetter(navProto, navigator, 'hardwareConcurrency', function() { return __hwCores; });
    defineProtoGetter(navProto, navigator, 'deviceMemory', function() { return __hwMem; });
    defineProtoGetter(navProto, navigator, 'maxTouchPoints', function() { return __touchPts; });
    defineProtoGetter(navProto, navigator, 'vendor', function() { return (__isIphone || __isMac) && __isSafariOnly ? 'Apple Computer, Inc.' : (__isFirefox ? '' : 'Google Inc.'); });
    defineProtoGetter(navProto, navigator, 'pdfViewerEnabled', function() { return !__isMobile; });

    // PluginArray & MimeTypeArray: desktop Chrome has 5 plugins — 0 = headless signal (mobile keeps native)
    try {
      if (!__isMobile) {
        var __plugNames = ['PDF Viewer', 'Chrome PDF Viewer', 'Chromium PDF Viewer', 'Microsoft Edge PDF Viewer', 'WebKit built-in PDF'];
        var __fakePlugins = [];
        for (var __pi = 0; __pi < __plugNames.length; __pi++) {
          __fakePlugins.push({ name: __plugNames[__pi], filename: 'internal-pdf-viewer', description: 'Portable Document Format', length: 1,
            item: function() { return { type: 'application/pdf', suffixes: 'pdf', description: 'Portable Document Format' }; },
            namedItem: function() { return null; } });
        }
        __fakePlugins.item = function(i) { return this[i] || null; };
        __fakePlugins.namedItem = function(n) { for (var k = 0; k < this.length; k++) { if (this[k] && this[k].name === n) return this[k]; } return null; };
        __fakePlugins.refresh = __cpaMaskNative(function() {}, 'refresh');
        defineProtoGetter(navProto, navigator, 'plugins', function() { return __fakePlugins; });
        var __fakeMimes = [
          { type: 'application/pdf', suffixes: 'pdf', description: 'Portable Document Format' },
          { type: 'text/pdf', suffixes: 'pdf', description: 'Portable Document Format' }
        ];
        __fakeMimes.item = function(i) { return this[i] || null; };
        __fakeMimes.namedItem = function(t) { for (var k = 0; k < this.length; k++) { if (this[k] && this[k].type === t) return this[k]; } return null; };
        defineProtoGetter(navProto, navigator, 'mimeTypes', function() { return __fakeMimes; });
      }
    } catch(e) {}

    // Keyboard layout map: real layout leaks locale habits — consistent map per spoofed language
    try {
      if (navigator.keyboard && navigator.keyboard.getLayoutMap) {
        var __kbdIsAr = (('$cleanLang' || '').indexOf('ar') === 0);
        var __kbdPairs = __kbdIsAr
          ? 'KeyQ:ض,KeyW:ص,KeyE:ث,KeyR:ق,KeyT:ف,KeyY:غ,KeyU:ع,KeyI:ه,KeyO:خ,KeyP:ح,BracketLeft:ج,BracketRight:د,KeyA:ش,KeyS:س,KeyD:ي,KeyF:ب,KeyG:ل,KeyH:ا,KeyJ:ت,KeyK:ن,KeyL:م,Semicolon:ك,Quote:ط,KeyZ:ئ,KeyX:ء,KeyC:ؤ,KeyV:ر,KeyB:لا,KeyN:ى,KeyM:ة,Comma:و,Period:ز,Slash:ظ,Digit1:1,Digit2:2,Digit3:3,Digit4:4,Digit5:5,Digit6:6,Digit7:7,Digit8:8,Digit9:9,Digit0:0,Space: ,Enter:Enter,Tab:Tab,Backspace:Backspace'
          : 'KeyQ:q,KeyW:w,KeyE:e,KeyR:r,KeyT:t,KeyY:y,KeyU:u,KeyI:i,KeyO:o,KeyP:p,BracketLeft:[,BracketRight:],KeyA:a,KeyS:s,KeyD:d,KeyF:f,KeyG:g,KeyH:h,KeyJ:j,KeyK:k,KeyL:l,Semicolon:;,Quote:\',KeyZ:z,KeyX:x,KeyC:c,KeyV:v,KeyB:b,KeyN:n,KeyM:m,Comma:,,Period:.,Slash:/,Digit1:1,Digit2:2,Digit3:3,Digit4:4,Digit5:5,Digit6:6,Digit7:7,Digit8:8,Digit9:9,Digit0:0,Space: ,Enter:Enter,Tab:Tab,Backspace:Backspace';
        var __kbdEntries = [];
        (function() {
          var parts = __kbdPairs.split(',');
          for (var i = 0; i < parts.length; i++) {
            var kv = parts[i].split(':');
            if (kv.length === 2) __kbdEntries.push([kv[0], kv[1]]);
          }
        })();
        var __kbdMapLike = {
          size: __kbdEntries.length,
          get: function(k) { for (var i = 0; i < __kbdEntries.length; i++) { if (__kbdEntries[i][0] === k) return __kbdEntries[i][1]; } return undefined; },
          has: function(k) { for (var i = 0; i < __kbdEntries.length; i++) { if (__kbdEntries[i][0] === k) return true; } return false; },
          keys: function() { var a = []; for (var i = 0; i < __kbdEntries.length; i++) a.push(__kbdEntries[i][0]); return a.values ? a.values() : a; },
          values: function() { var a = []; for (var i = 0; i < __kbdEntries.length; i++) a.push(__kbdEntries[i][1]); return a.values ? a.values() : a; },
          entries: function() { return __kbdEntries.slice(); },
          forEach: function(cb, th) { for (var i = 0; i < __kbdEntries.length; i++) cb.call(th, __kbdEntries[i][1], __kbdEntries[i][0], this); }
        };
        try {
          var __kbdProto = Object.getPrototypeOf(navigator.keyboard) || navigator.keyboard;
          __kbdProto.getLayoutMap = __cpaMaskNative(function() { return Promise.resolve(__kbdMapLike); }, 'getLayoutMap');
        } catch(e) {
          try { navigator.keyboard.getLayoutMap = __cpaMaskNative(function() { return Promise.resolve(__kbdMapLike); }, 'getLayoutMap'); } catch(e2) {}
        }
      }
    } catch(e) {}

    // SpeechSynthesis voices: real device voices (e.g. emulator packs) are a leak — plausible list per language
    try {
      if (window.speechSynthesis) {
        var __voiceLang = (('$cleanLang' || '').indexOf('ar') === 0) ? 'ar-SA' : 'en-US';
        var __fakeVoices = [
          { name: __voiceLang.indexOf('ar') === 0 ? 'Microsoft Hoda - Arabic (Saudi Arabia)' : 'Google US English', lang: __voiceLang, localService: true, default: true, voiceURI: __voiceLang.indexOf('ar') === 0 ? 'Microsoft Hoda' : 'Google US English' },
          { name: __voiceLang.indexOf('ar') === 0 ? 'Google Arabic' : 'Google UK English Female', lang: __voiceLang.indexOf('ar') === 0 ? 'ar' : 'en-GB', localService: true, default: false, voiceURI: __voiceLang.indexOf('ar') === 0 ? 'Google Arabic' : 'Google UK English Female' }
        ];
        try {
          Object.defineProperty(window.speechSynthesis, 'getVoices', {
            value: __cpaMaskNative(function() { return __fakeVoices.slice(); }, 'getVoices'),
            configurable: true
          });
        } catch(e) {}
        try {
          Object.defineProperty(window.speechSynthesis, 'pending', { get: __cpaMaskNative(function() { return false; }, 'get pending'), configurable: true });
          Object.defineProperty(window.speechSynthesis, 'speaking', { get: __cpaMaskNative(function() { return false; }, 'get speaking'), configurable: true });
        } catch(e) {}
      }
    } catch(e) {}

    // WebGPU: requestAdapter leaks the REAL GPU architecture — spoof adapter matching our renderer story
    try {
      var __gpuArch = 'adreno-740', __gpuVend = 'qualcomm', __gpuDev = '';
      try {
        var __gr = (__glRenderer || '').toLowerCase();
        if (__gr.indexOf('mali-g715') !== -1) { __gpuVend = 'arm'; __gpuArch = 'mali-g715'; }
        else if (__gr.indexOf('mali-g710') !== -1) { __gpuVend = 'arm'; __gpuArch = 'mali-g710'; }
        else if (__gr.indexOf('xclipse') !== -1) { __gpuVend = 'samsung'; __gpuArch = 'xclipse-920'; }
        else if (__gr.indexOf('adreno') !== -1) { __gpuVend = 'qualcomm'; __gpuArch = 'adreno-740'; }
        else if (__gr.indexOf('apple') !== -1) { __gpuVend = 'apple'; __gpuArch = 'apple-m2'; }
        else if (__gr.indexOf('uhd graphics 770') !== -1) { __gpuVend = 'intel'; __gpuArch = 'gen-12lp'; }
        else if (__gr.indexOf('rx 6600') !== -1) { __gpuVend = 'amd'; __gpuArch = 'rdna-2'; }
        else if (__gr.indexOf('rtx 4060') !== -1) { __gpuVend = 'nvidia'; __gpuArch = 'ada'; }
        else if (__gr.indexOf('1660') !== -1) { __gpuVend = 'nvidia'; __gpuArch = 'turing'; }
        else { __gpuVend = 'nvidia'; __gpuArch = 'ampere'; }
        __gpuDev = __gpuArch;
      } catch(e) {}
      var __fakeAdapterInfo = { vendor: __gpuVend, architecture: __gpuArch, device: __gpuDev, description: '' };
      var __fakeAdapter = {
        info: __fakeAdapterInfo,
        requestDevice: __cpaMaskNative(function() {
          return Promise.resolve({
            label: '', lost: Promise.resolve({ reason: 'destroyed', message: '' }),
            createBuffer: function() { throw new Error('Not supported'); },
            destroy: function() {}
          });
        }, 'requestDevice')
      };
      var __gpuObj = null;
      try { __gpuObj = navigator.gpu || null; } catch(e) {}
      if (!__gpuObj && !__isMobile) {
        // Desktop UA without WebGPU = mismatch (real desktop Chrome 113+ has it)
        __gpuObj = {};
        try {
          Object.defineProperty(navigator, 'gpu', { value: __gpuObj, enumerable: true, configurable: true });
        } catch(e) {}
      }
      if (__gpuObj) {
        try {
          __gpuObj.requestAdapter = __cpaMaskNative(function() {
            try { if (window.__cpaBridge) window.__cpaBridge.note('webgpu'); } catch(e) {}
            return Promise.resolve(__fakeAdapter);
          }, 'requestAdapter');
        } catch(e) {}
      }
    } catch(e) {}

    // Desktop-UA on a phone: vibrate/virtualKeyboard/presentation must not leak mobile hardware
    try {
      if (!__isMobile) {
        try {
          if (navigator.vibrate) {
            navigator.vibrate = __cpaMaskNative(function() { return false; }, 'vibrate');
          }
        } catch(e) {}
        try {
          Object.defineProperty(navigator, 'virtualKeyboard', { get: __cpaMaskNative(function() { return undefined; }, 'get virtualKeyboard'), enumerable: true, configurable: true });
        } catch(e) {}
        try {
          Object.defineProperty(navigator, 'presentation', { get: __cpaMaskNative(function() { return undefined; }, 'get presentation'), enumerable: true, configurable: true });
        } catch(e) {}
        // Touch APIs: 'ontouchstart' in window is TRUE on a phone even with desktop UA — delete it
        try { delete window.ontouchstart; } catch(e) {}
        try {
          Object.defineProperty(window, 'TouchEvent', { get: __cpaMaskNative(function() { return undefined; }, 'get TouchEvent'), configurable: true });
        } catch(e) {}
        try {
          Object.defineProperty(window, 'Touch', { get: __cpaMaskNative(function() { return undefined; }, 'get Touch'), configurable: true });
        } catch(e) {}
      } else {
        // Mobile-UA: touch MUST exist (its absence = emulator signal)
        try {
          if (!('ontouchstart' in window)) {
            Object.defineProperty(window, 'ontouchstart', { value: null, writable: true, enumerable: true, configurable: true });
          }
        } catch(e) {}
      }
    } catch(e) {}

    // Permissions API: never leak 'denied' for camera/mic (fresh browsers report 'prompt')
    try {
      if (navigator.permissions && navigator.permissions.query) {
        var __origPermQuery = navigator.permissions.query.bind(navigator.permissions);
        navigator.permissions.query = __cpaMaskNative(function(params) {
          return __origPermQuery(params).then(function(status) {
            try {
              if (params && (params.name === 'camera' || params.name === 'microphone') && status && status.state === 'denied') {
                return { state: 'prompt', onchange: null };
              }
            } catch(e) {}
            return status;
          });
        }, 'query');
      }
    } catch(e) {}

    // AudioContext fingerprint: deterministic per-session LSB noise on offline render (stable across reads)
    try {
      if (window.OfflineAudioContext && !OfflineAudioContext.prototype.__cpaAudioPatched) {
        Object.defineProperty(OfflineAudioContext.prototype, '__cpaAudioPatched', { value: true, enumerable: false, configurable: true });
        var __origStartRendering = OfflineAudioContext.prototype.startRendering;
        OfflineAudioContext.prototype.startRendering = __cpaMaskNative(function() {
          return __origStartRendering.call(this).then(function(buffer) {
            try {
              if (buffer && buffer.numberOfChannels && buffer.length > 32) {
                var ch0 = buffer.getChannelData(0);
                var step = Math.max(97, (buffer.length / 2048) | 0);
                for (var ai = 0; ai < ch0.length; ai += step) {
                  var ah = __cpaHash(ai | 0);
                  ch0[ai] = ch0[ai] + ((ah & 1) ? 0.0000007 : -0.0000007);
                }
              }
            } catch(e) {}
            return buffer;
          });
        }, 'startRendering');
      }
    } catch(e) {}

    // DOMRect fingerprint: stable per-element sub-pixel offset (identical on every read — no drift signal)
    try {
      if (window.Element && Element.prototype.getClientRects && !Element.prototype.__cpaRectPatched) {
        Object.defineProperty(Element.prototype, '__cpaRectPatched', { value: true, enumerable: false, configurable: true });
        var __origGCR = Element.prototype.getClientRects;
        Element.prototype.getClientRects = __cpaMaskNative(function() {
          var rects = __origGCR.call(this);
          try {
            if (rects && rects.length > 0) {
              var tag = (this.tagName || 'E') + '|' + (this.className || '') + '|' + rects.length;
              var hh = 0;
              for (var ci = 0; ci < tag.length; ci++) hh = ((hh * 31) + tag.charCodeAt(ci)) >>> 0;
              hh = __cpaHash(hh);
              var jx = (((hh % 1000) / 1000) - 0.5) * 0.06;
              var jy = ((((hh >>> 10) % 1000) / 1000) - 0.5) * 0.06;
              if (jx !== 0 || jy !== 0) {
                var out = [];
                for (var ri = 0; ri < rects.length; ri++) {
                  var r = rects[ri];
                  out.push({ x: r.x + jx, y: r.y + jy, width: r.width, height: r.height,
                    top: r.top + jy, right: r.right + jx, bottom: r.bottom + jy, left: r.left + jx,
                    toJSON: function() { return this; } });
                }
                return out;
              }
            }
          } catch(e) {}
          return rects;
        }, 'getClientRects');
      }
    } catch(e) {}

    if (__rawUa.length > 0 && __rawUa.toLowerCase() !== 'random') {
      defineProtoGetter(navProto, navigator, 'userAgent', function() { return __rawUa; });
      defineProtoGetter(navProto, navigator, 'appVersion', function() { return __rawUa.replace(/^Mozilla\//, ''); });
    }

    // Visibility, Focus & Window Dimensions
    try {
      var docProto = (typeof Document !== 'undefined' && Document.prototype) ? Document.prototype : null;
      defineProtoGetter(docProto, document, 'hidden', function() { return false; });
      defineProtoGetter(docProto, document, 'visibilityState', function() { return 'visible'; });
      if (docProto) {
        docProto.hasFocus = __cpaMaskNative(function() { return true; }, 'hasFocus');
      } else {
        document.hasFocus = __cpaMaskNative(function() { return true; }, 'hasFocus');
      }
      if (!window.outerWidth || window.outerWidth === 0) {
        Object.defineProperty(window, 'outerWidth', { get: __cpaMaskNative(function() { return window.innerWidth || screen.width || (__isMobile ? 412 : 1920); }, 'get outerWidth'), configurable: true });
      }
      if (!window.outerHeight || window.outerHeight === 0) {
        Object.defineProperty(window, 'outerHeight', { get: __cpaMaskNative(function() { return window.innerHeight || screen.height || (__isMobile ? 915 : 1080); }, 'get outerHeight'), configurable: true });
      }
    } catch(e) {}

    // Battery API Spoofing (prevents emulator/charging=1.0 detection)
    try {
      if (navProto || navigator) {
        var batteryLevel = Number((0.64 + ((__cpaSeed % 28) / 100)).toFixed(2));
        var batteryObj = {
          charging: false,
          chargingTime: Infinity,
          dischargingTime: 12600 + (__cpaSeed % 5400),
          level: batteryLevel,
          onchargingchange: null,
          onchargingtimechange: null,
          ondischargingtimechange: null,
          onlevelchange: null,
          addEventListener: __cpaMaskNative(function() {}, 'addEventListener'),
          removeEventListener: __cpaMaskNative(function() {}, 'removeEventListener'),
          dispatchEvent: __cpaMaskNative(function() { return true; }, 'dispatchEvent')
        };
        var getBatteryFn = __cpaMaskNative(function() { return Promise.resolve(batteryObj); }, 'getBattery');
        if (navProto) navProto.getBattery = getBatteryFn;
        else navigator.getBattery = getBatteryFn;
      }
    } catch(e) {}

    // NetworkInformation API Spoofing (prevents VPN/Emulator RTT=0 detection)
    try {
      if (navigator.connection) {
        var connProto = Object.getPrototypeOf(navigator.connection);
        defineProtoGetter(connProto, navigator.connection, 'effectiveType', function() { return '4g'; });
        defineProtoGetter(connProto, navigator.connection, 'rtt', function() { return __netRtt; });
        defineProtoGetter(connProto, navigator.connection, 'downlink', function() { return __netDl; });
        defineProtoGetter(connProto, navigator.connection, 'saveData', function() { return false; });
      }
    } catch(e) {}

    // Polyfill window.Notification
    if (typeof window.Notification === 'undefined') {
      var FakeNotification = __cpaMaskNative(function(title, options) {
        this.title = title || '';
        this.options = options || {};
        this.close = __cpaMaskNative(function() {}, 'close');
      }, 'Notification');
      FakeNotification.permission = 'default';
      FakeNotification.requestPermission = __cpaMaskNative(function(cb) {
        if (typeof cb === 'function') { setTimeout(function() { try { cb('default'); } catch(e) {} }, 40); }
        return Promise.resolve('default');
      }, 'requestPermission');
      window.Notification = FakeNotification;
    }

    if (!__isFirefox && !__isSafariOnly) {
      if (!window.chrome) window.chrome = {};
      if (!window.chrome.runtime) {
        window.chrome.runtime = {
          PlatformOs: { ANDROID: 'android', CROS: 'cros', LINUX: 'linux', MAC: 'mac', OPENBSD: 'openbsd', WIN: 'win' },
          PlatformArch: { ARM: 'arm', ARM64: 'arm64', MIPS: 'mips', MIPS64: 'mips64', X86_32: 'x86-32', X86_64: 'x86-64' },
          PlatformNaclArch: { ARM: 'arm', MIPS: 'mips', MIPS64: 'mips64', X86_32: 'x86-32', X86_64: 'x86-64' }
        };
      }
      if (!window.chrome.loadTimes) window.chrome.loadTimes = __cpaMaskNative(function() { return {}; }, 'loadTimes');
      if (!window.chrome.csi) window.chrome.csi = __cpaMaskNative(function() { return {}; }, 'csi');
      if (!window.chrome.app) window.chrome.app = { isInstalled: false };

      // Dynamic Chromium Client Hints (100% synchronized with active User-Agent version & model)
      var uaData = {
        brands: [
          { brand: 'Chromium', version: __chromeMajorVer },
          { brand: 'Not/A)Brand', version: '8' },
          { brand: 'Google Chrome', version: __chromeMajorVer }
        ],
        mobile: __isMobile,
        platform: __clientPlat,
        getHighEntropyValues: __cpaMaskNative(function(hints) {
          return Promise.resolve({
            architecture: __isMobile ? 'arm' : 'x86',
            bitness: '64',
            brands: this.brands,
            fullVersionList: [
              { brand: 'Chromium', version: __chromeFullVer },
              { brand: 'Not/A)Brand', version: '8.0.0.0' },
              { brand: 'Google Chrome', version: __chromeFullVer }
            ],
            mobile: __isMobile,
            model: __deviceModel,
            platform: __clientPlat,
            platformVersion: __platVer,
            uaFullVersion: __chromeFullVer
          });
        }, 'getHighEntropyValues'),
        toJSON: __cpaMaskNative(function() {
          return { brands: this.brands, mobile: this.mobile, platform: this.platform };
        }, 'toJSON')
      };
      defineProtoGetter(navProto, navigator, 'userAgentData', function() { return uaData; });
    }

    // Screen: seeded real-device geometry consistent with the UA class (inner viewport stays real)
    try {
      var scrProto = (typeof Screen !== 'undefined' && Screen.prototype) ? Screen.prototype : null;
      defineProtoGetter(scrProto, screen, 'colorDepth', function() { return 24; });
      defineProtoGetter(scrProto, screen, 'pixelDepth', function() { return 24; });
      defineProtoGetter(scrProto, screen, 'width', function() { return __scrW; });
      defineProtoGetter(scrProto, screen, 'height', function() { return __scrH; });
      defineProtoGetter(scrProto, screen, 'availWidth', function() { return __scrW; });
      defineProtoGetter(scrProto, screen, 'availHeight', function() { return __scrAvailH; });
      defineProtoGetter(scrProto, screen, 'availLeft', function() { return 0; });
      defineProtoGetter(scrProto, screen, 'availTop', function() { return 0; });
      try {
        Object.defineProperty(window, 'devicePixelRatio', { get: __cpaMaskNative(function() { return __scrDpr; }, 'get devicePixelRatio'), configurable: true });
      } catch(e) {}
    } catch(e) {}

    // Deterministic Per-Session Canvas & Audio Fingerprint Shield (Defeats multi-read determinism checks)
    try {
      function applyDeterministicNoise(imageData) {
        if (!imageData || !imageData.data) return;
        var data = imageData.data;
        var len = data.length;
        if (len < 64) return;
        var step = len > 40000 ? 64 : 16;
        for (var i = 0; i < len - 4; i += step) {
          if (data[i + 3] > 16) {
            var h = __cpaHash(i);
            var ch = h % 3;
            var delta = (h & 4) ? 1 : -1;
            var v = data[i + ch];
            // Flip least-significant bit deterministically based on session seed
            data[i + ch] = ((v + __cpaSeed) & 1) ? Math.min(255, v + delta) : v;
          }
        }
      }

      if (window.CanvasRenderingContext2D && !CanvasRenderingContext2D.prototype.__cpaCanvasPatched) {
        Object.defineProperty(CanvasRenderingContext2D.prototype, '__cpaCanvasPatched', { value: true, enumerable: false, configurable: true });
        var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
        var origPutImageData = CanvasRenderingContext2D.prototype.putImageData;

        CanvasRenderingContext2D.prototype.getImageData = __cpaMaskNative(function(sx, sy, sw, sh) {
          var imgData = origGetImageData.apply(this, arguments);
          applyDeterministicNoise(imgData);
          return imgData;
        }, 'getImageData');

        if (window.HTMLCanvasElement) {
          var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
          var origToBlob = HTMLCanvasElement.prototype.toBlob;

          function cloneAndPerturbCanvas(srcCanvas) {
            try {
              if (!srcCanvas || srcCanvas.width === 0 || srcCanvas.height === 0 || srcCanvas.width > 2048 || srcCanvas.height > 2048) return srcCanvas;
              var clone = document.createElement('canvas');
              clone.width = srcCanvas.width;
              clone.height = srcCanvas.height;
              var cctx = clone.getContext('2d');
              if (!cctx) return srcCanvas;
              cctx.drawImage(srcCanvas, 0, 0);
              var sw = Math.min(clone.width, 64);
              var sh = Math.min(clone.height, 64);
              var slice = origGetImageData.call(cctx, 0, 0, sw, sh);
              applyDeterministicNoise(slice);
              origPutImageData.call(cctx, slice, 0, 0);
              return clone;
            } catch(e) {
              return srcCanvas;
            }
          }

          HTMLCanvasElement.prototype.toDataURL = __cpaMaskNative(function() {
            var targetCanvas = cloneAndPerturbCanvas(this);
            return origToDataURL.apply(targetCanvas, arguments);
          }, 'toDataURL');

          HTMLCanvasElement.prototype.toBlob = __cpaMaskNative(function() {
            var targetCanvas = cloneAndPerturbCanvas(this);
            return origToBlob.apply(targetCanvas, arguments);
          }, 'toBlob');
        }
      }

      if (window.AudioBuffer && !AudioBuffer.prototype.__cpaAudioPatched) {
        Object.defineProperty(AudioBuffer.prototype, '__cpaAudioPatched', { value: true, enumerable: false, configurable: true });
        var patchedAudioBuffers = typeof WeakSet === 'function' ? new WeakSet() : null;
        var origGetChannelData = AudioBuffer.prototype.getChannelData;
        AudioBuffer.prototype.getChannelData = __cpaMaskNative(function() {
          var data = origGetChannelData.apply(this, arguments);
          try {
            if (data && data.length > 0 && (!patchedAudioBuffers || !patchedAudioBuffers.has(data))) {
              if (patchedAudioBuffers) patchedAudioBuffers.add(data);
              for (var i = 0; i < Math.min(data.length, 16); i++) {
                var offset = ((__cpaHash(i) % 11) - 5) * 1e-7;
                data[i] = data[i] + offset;
              }
            }
          } catch(e) {}
          return data;
        }, 'getChannelData');
      }
    } catch(e) {}
  } catch(e) {}

  // 3. Deep Timezone, Intl.DateTimeFormat & Date.prototype Synchronization
  try {
    var rawTz = '$cleanTz';
    var rawLang = '$cleanLang';
    var tz = (rawTz && rawTz.indexOf('/') !== -1 && rawTz.indexOf('{') === -1) ? rawTz.trim() : 'America/New_York';
    var lang = (rawLang && /^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?$/.test(rawLang.trim())) ? rawLang.trim() : 'en-US';
    Object.defineProperty(window, '__cpaTargetTz', { value: tz, enumerable: false, writable: true, configurable: true });
    Object.defineProperty(window, '__cpaTargetLang', { value: lang, enumerable: false, writable: true, configurable: true });

    var navP = (typeof Navigator !== 'undefined' && Navigator.prototype) ? Navigator.prototype : null;
    defineProtoGetter(navP, navigator, 'language', function() { return window.__cpaTargetLang || lang; });
    defineProtoGetter(navP, navigator, 'languages', function() { var l = window.__cpaTargetLang || lang; return [l, l.split('-')[0]]; });

    if (window.Intl && Intl.DateTimeFormat && !window.__cpaOrigDateTimeFormat) {
      var OrigDTF = Intl.DateTimeFormat;
      Object.defineProperty(window, '__cpaOrigDateTimeFormat', { value: OrigDTF, enumerable: false, configurable: true });
      var origResolved = OrigDTF.prototype.resolvedOptions;

      var SpoofedDTF = __cpaMaskNative(function(locales, options) {
        var activeTz = window.__cpaTargetTz || tz;
        var activeLang = window.__cpaTargetLang || lang;
        var effLocales = locales !== undefined ? locales : activeLang;
        var effOpts = options ? Object.assign({}, options) : {};
        if (!effOpts.timeZone) effOpts.timeZone = activeTz;
        return new OrigDTF(effLocales, effOpts);
      }, 'DateTimeFormat');
      SpoofedDTF.prototype = OrigDTF.prototype;
      SpoofedDTF.supportedLocalesOf = __cpaMaskNative(OrigDTF.supportedLocalesOf.bind(OrigDTF), 'supportedLocalesOf');
      Object.defineProperty(OrigDTF.prototype, 'constructor', { value: SpoofedDTF, configurable: true, writable: true });
      OrigDTF.prototype.resolvedOptions = __cpaMaskNative(function() {
        var res = origResolved.call(this);
        try { if (!res.timeZone) res.timeZone = window.__cpaTargetTz || tz; } catch(e) {}
        return res;
      }, 'resolvedOptions');
      Intl.DateTimeFormat = SpoofedDTF;
    }

    function computeTzOffsetForDate(dateObj, ianaTz) {
      try {
        var d = (dateObj instanceof Date && !isNaN(dateObj.getTime())) ? dateObj : new Date();
        var OrigDTF = window.__cpaOrigDateTimeFormat || Intl.DateTimeFormat;
        var utcStr = d.toLocaleString('en-US', { timeZone: 'UTC', hour12: false });
        var tzStr = d.toLocaleString('en-US', { timeZone: ianaTz, hour12: false });
        return Math.round((Date.parse(utcStr) - Date.parse(tzStr)) / 60000);
      } catch(err) {
        return 300;
      }
    }

    if (!Date.prototype.__cpaDatePatched) {
      Object.defineProperty(Date.prototype, '__cpaDatePatched', { value: true, enumerable: false, configurable: true });
      var origToLocaleString = Date.prototype.toLocaleString;
      var origToLocaleDateString = Date.prototype.toLocaleDateString;
      var origToLocaleTimeString = Date.prototype.toLocaleTimeString;
      var origDateToString = Date.prototype.toString;
      var origTimeString = Date.prototype.toTimeString;

      Date.prototype.getTimezoneOffset = __cpaMaskNative(function() {
        return computeTzOffsetForDate(this, window.__cpaTargetTz || tz);
      }, 'getTimezoneOffset');

      Date.prototype.toLocaleString = __cpaMaskNative(function(locales, options) {
        var opts = options ? Object.assign({}, options) : {};
        if (!opts.timeZone) opts.timeZone = window.__cpaTargetTz || tz;
        return origToLocaleString.call(this, locales || window.__cpaTargetLang || lang, opts);
      }, 'toLocaleString');

      Date.prototype.toLocaleDateString = __cpaMaskNative(function(locales, options) {
        var opts = options ? Object.assign({}, options) : {};
        if (!opts.timeZone) opts.timeZone = window.__cpaTargetTz || tz;
        return origToLocaleDateString.call(this, locales || window.__cpaTargetLang || lang, opts);
      }, 'toLocaleDateString');

      Date.prototype.toLocaleTimeString = __cpaMaskNative(function(locales, options) {
        var opts = options ? Object.assign({}, options) : {};
        if (!opts.timeZone) opts.timeZone = window.__cpaTargetTz || tz;
        return origToLocaleTimeString.call(this, locales || window.__cpaTargetLang || lang, opts);
      }, 'toLocaleTimeString');

      function formatGmtOffset(offsetMin) {
        var sign = offsetMin <= 0 ? '+' : '-';
        var abs = Math.abs(offsetMin);
        var hh = String(Math.floor(abs / 60)).padStart(2, '0');
        var mm = String(abs % 60).padStart(2, '0');
        return 'GMT' + sign + hh + mm;
      }

      Date.prototype.toString = __cpaMaskNative(function() {
        try {
          if (isNaN(this.getTime())) return origDateToString.call(this);
          var activeTz = window.__cpaTargetTz || tz;
          var off = computeTzOffsetForDate(this, activeTz);
          var shifted = new Date(this.getTime() - off * 60000);
          var parts = shifted.toUTCString().replace(',', '').split(' ');
          // parts: [DayName, DD, Mon, YYYY, HH:MM:SS, 'GMT']
          if (parts.length >= 5) {
            return parts[0] + ' ' + parts[2] + ' ' + parts[1] + ' ' + parts[3] + ' ' + parts[4] + ' ' + formatGmtOffset(off) + ' (' + activeTz.replace('_', ' ') + ')';
          }
        } catch(e) {}
        return origDateToString.call(this);
      }, 'toString');

      Date.prototype.toTimeString = __cpaMaskNative(function() {
        try {
          if (isNaN(this.getTime())) return origTimeString.call(this);
          var activeTz = window.__cpaTargetTz || tz;
          var off = computeTzOffsetForDate(this, activeTz);
          var shifted = new Date(this.getTime() - off * 60000);
          var parts = shifted.toUTCString().replace(',', '').split(' ');
          if (parts.length >= 5) {
            return parts[4] + ' ' + formatGmtOffset(off) + ' (' + activeTz.replace('_', ' ') + ')';
          }
        } catch(e) {}
        return origTimeString.call(this);
      }, 'toTimeString');
    }
  } catch(e) {}

  // 4. HTML5 Geolocation Spoofing (Matches Proxy Latitude / Longitude exactly & Native-Cloaked)
  try {
    if (navigator.geolocation) {
      var geoProto = Object.getPrototypeOf(navigator.geolocation) || navigator.geolocation;
      var makeGeoPos = function() {
        return {
          coords: {
            latitude: $latitude,
            longitude: $longitude,
            accuracy: 18.5,
            altitude: null,
            altitudeAccuracy: null,
            heading: null,
            speed: null
          },
          timestamp: Date.now()
        };
      };
      geoProto.getCurrentPosition = __cpaMaskNative(function(success, error, options) {
        if (typeof success === 'function') {
          setTimeout(function() { try { success(makeGeoPos()); } catch(e) {} }, 25);
        }
      }, 'getCurrentPosition');
      geoProto.watchPosition = __cpaMaskNative(function(success, error, options) {
        if (typeof success === 'function') {
          setTimeout(function() { try { success(makeGeoPos()); } catch(e) {} }, 25);
        }
        return 1;
      }, 'watchPosition');
      geoProto.clearWatch = __cpaMaskNative(function(id) {}, 'clearWatch');
    }
  } catch(e) {}

  // 5. WebRTC Configuration & IP Spoofing + Synchronous Iframe & Worker Shield
  try {
    var mode = '$webrtcMode';
    var rawTargetIp = ('$cleanIp' || '').trim();
    var ipv4Regex = /^(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(?:\.(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}$/;
    var fallbackOct2 = 24 + ((__cpaSeed >>> 8) % 40);
    var fallbackOct3 = 10 + ((__cpaSeed >>> 16) % 220);
    var fallbackOct4 = 12 + ((__cpaSeed >>> 24) % 230);
    var targetIp = ipv4Regex.test(rawTargetIp) ? rawTargetIp : ('104.' + fallbackOct2 + '.' + fallbackOct3 + '.' + fallbackOct4);
    var localIp = '192.168.1.' + (101 + (__cpaSeed % 45));
    Object.defineProperty(window, '__cpaWebRtcMode', { value: mode, enumerable: false, writable: true, configurable: true });
    Object.defineProperty(window, '__cpaTargetIp', { value: targetIp, enumerable: false, writable: true, configurable: true });

    // Spoof navigator.userActivation so gesture-gated Smartlinks see active user gesture
    try {
      if (navigator.userActivation) {
        var uaProto = Object.getPrototypeOf(navigator.userActivation) || navigator.userActivation;
        defineProtoGetter(uaProto, navigator.userActivation, 'isActive', function() { return true; });
        defineProtoGetter(uaProto, navigator.userActivation, 'hasBeenActive', function() { return true; });
      }
    } catch(e) {}

    function applyWebRtcToWindow(targetWin) {
      if (!targetWin) return;
      try {
        var activeMode = window.__cpaWebRtcMode || mode;
        var activeIp = window.__cpaTargetIp || targetIp;
        if (!ipv4Regex.test(activeIp)) activeIp = targetIp;

        if (activeMode === 'disabled' || activeMode === 'block') {
          var webRtcProps = [
            'RTCPeerConnection',
            'webkitRTCPeerConnection',
            'mozRTCPeerConnection',
            'msRTCPeerConnection',
            'RTCDataChannel',
            'RTCIceCandidate',
            'webkitRTCIceCandidate',
            'mozRTCIceCandidate',
            'RTCSessionDescription',
            'webkitRTCSessionDescription',
            'mozRTCSessionDescription',
            'RTCRtpSender',
            'RTCRtpReceiver',
            'RTCRtpTransceiver',
            'RTCDtlsTransport',
            'RTCIceTransport',
            'RTCSctpTransport',
            'RTCCertificate'
          ];
          webRtcProps.forEach(function(prop) {
            try { delete targetWin[prop]; } catch(e) {}
            try {
              if (targetWin.Window && targetWin.Window.prototype) {
                delete targetWin.Window.prototype[prop];
              }
            } catch(e) {}
            try {
              Object.defineProperty(targetWin, prop, {
                get: __cpaMaskNative(function() { return undefined; }, 'get ' + prop),
                set: __cpaMaskNative(function() {}, 'set ' + prop),
                configurable: true,
                enumerable: false
              });
            } catch(ex) {
              try { targetWin[prop] = undefined; } catch(ex2) {}
            }
          });
          try {
            if (targetWin.navigator) {
              delete targetWin.navigator.webkitGetUserMedia;
              delete targetWin.navigator.mozGetUserMedia;
              delete targetWin.navigator.getUserMedia;
              if (targetWin.navigator.mediaDevices) {
                targetWin.navigator.mediaDevices.getUserMedia = __cpaMaskNative(function() {
                  return Promise.reject(new DOMException('Permission denied', 'NotAllowedError'));
                }, 'getUserMedia');
                targetWin.navigator.mediaDevices.enumerateDevices = __cpaMaskNative(function() {
                  return Promise.resolve([]);
                }, 'enumerateDevices');
              }
            }
          } catch(e) {}
          return;
        }

        if (activeMode !== 'spoof') return;

        // High-Fidelity WebRTC Spoofing Engine:
        var FakeRTCIceCandidate = __cpaMaskNative(function(init) {
          if (!init) init = {};
          var ipNow = (window.__cpaTargetIp && ipv4Regex.test(window.__cpaTargetIp)) ? window.__cpaTargetIp : activeIp;
          this.candidate = init.candidate || ('candidate:2130706431 1 udp 2122260223 ' + ipNow + ' 54322 typ srflx raddr ' + localIp + ' rport 54321 generation 0 ufrag abcd network-id 1');
          this.sdpMid = init.sdpMid !== undefined ? init.sdpMid : '0';
          this.sdpMLineIndex = init.sdpMLineIndex !== undefined ? init.sdpMLineIndex : 0;
          this.usernameFragment = init.usernameFragment || 'abcd';
          this.foundation = init.foundation || '2130706431';
          this.component = init.component || 'rtp';
          this.priority = init.priority || 2122260223;
          this.address = init.address || ipNow;
          this.protocol = init.protocol || 'udp';
          this.port = init.port || 54322;
          this.type = init.type || 'srflx';
          this.tcpType = null;
          this.relatedAddress = init.relatedAddress || localIp;
          this.relatedPort = init.relatedPort || 54321;
        }, 'RTCIceCandidate');
        FakeRTCIceCandidate.prototype.toJSON = __cpaMaskNative(function() {
          return {
            candidate: this.candidate,
            sdpMid: this.sdpMid,
            sdpMLineIndex: this.sdpMLineIndex,
            usernameFragment: this.usernameFragment
          };
        }, 'toJSON');

        var FakeRTCSessionDescription = __cpaMaskNative(function(init) {
          if (!init) init = {};
          this.type = init.type || 'offer';
          this.sdp = init.sdp || '';
        }, 'RTCSessionDescription');
        FakeRTCSessionDescription.prototype.toJSON = __cpaMaskNative(function() {
          return { type: this.type, sdp: this.sdp };
        }, 'toJSON');

        function buildSpoofedSdp() {
          var ipNow = (window.__cpaTargetIp && ipv4Regex.test(window.__cpaTargetIp)) ? window.__cpaTargetIp : activeIp;
          return [
            'v=0',
            'o=- 422319207012345678 2 IN IP4 127.0.0.1',
            's=-',
            't=0 0',
            'a=group:BUNDLE 0',
            'a=msid-semantic: WMS',
            'm=application 9 UDP/DTLS/SCTP webrtc-datachannel',
            'c=IN IP4 ' + ipNow,
            'a=candidate:842163049 1 udp 2122199295 ' + localIp + ' 54321 typ host generation 0 ufrag abcd network-id 1',
            'a=candidate:2130706431 1 udp 2122260223 ' + ipNow + ' 54322 typ srflx raddr ' + localIp + ' rport 54321 generation 0 ufrag abcd network-id 1',
            'a=ice-ufrag:abcd',
            'a=ice-pwd:abcdefghijklmnopqrstuvwxyz01',
            'a=ice-options:trickle',
            'a=fingerprint:sha-256 00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF',
            'a=setup:actpass',
            'a=mid:0',
            'a=sctp-port:5000',
            'a=max-message-size:262144',
            ''
          ].join('\r\n');
        }

        var FakeRTCPeerConnection = __cpaMaskNative(function(config) {
          this.config = config || {};
          this.localDescription = null;
          this.remoteDescription = null;
          this.currentLocalDescription = null;
          this.pendingLocalDescription = null;
          this.currentRemoteDescription = null;
          this.pendingRemoteDescription = null;
          this.signalingState = 'stable';
          this.iceGatheringState = 'new';
          this.iceConnectionState = 'new';
          this.connectionState = 'new';
          this.canTrickleIceCandidates = true;

          this.onicecandidate = null;
          this.onicecandidateerror = null;
          this.onicegatheringstatechange = null;
          this.oniceconnectionstatechange = null;
          this.onsignalingstatechange = null;
          this.onconnectionstatechange = null;
          this.onnegotiationneeded = null;
          this.ondatachannel = null;
          this.ontrack = null;
          Object.defineProperty(this, '__listeners', { value: {}, enumerable: false, writable: true, configurable: true });
        }, 'RTCPeerConnection');

        FakeRTCPeerConnection.prototype.addEventListener = __cpaMaskNative(function(type, listener) {
          if (typeof listener !== 'function') return;
          if (!this.__listeners) Object.defineProperty(this, '__listeners', { value: {}, enumerable: false, writable: true, configurable: true });
          if (!this.__listeners[type]) this.__listeners[type] = [];
          this.__listeners[type].push(listener);
        }, 'addEventListener');

        FakeRTCPeerConnection.prototype.removeEventListener = __cpaMaskNative(function(type, listener) {
          if (!this.__listeners || !this.__listeners[type]) return;
          this.__listeners[type] = this.__listeners[type].filter(function(l) { return l !== listener; });
        }, 'removeEventListener');

        FakeRTCPeerConnection.prototype.dispatchEvent = __cpaMaskNative(function(event) {
          var self = this;
          var type = event && event.type ? event.type : '';
          var handler = self['on' + type];
          if (typeof handler === 'function') {
            try { handler.call(self, event); } catch(e) {}
          }
          if (self.__listeners && self.__listeners[type]) {
            self.__listeners[type].forEach(function(l) {
              try { l.call(self, event); } catch(e) {}
            });
          }
          return true;
        }, 'dispatchEvent');

        FakeRTCPeerConnection.prototype.createDataChannel = __cpaMaskNative(function(label, options) {
          var dc = {
            label: label || '',
            ordered: true,
            protocol: '',
            id: 0,
            readyState: 'open',
            bufferedAmount: 0,
            onopen: null,
            onclose: null,
            onerror: null,
            onmessage: null,
            send: __cpaMaskNative(function() {}, 'send'),
            close: __cpaMaskNative(function() { this.readyState = 'closed'; if (this.onclose) this.onclose(); }, 'close'),
            addEventListener: __cpaMaskNative(function() {}, 'addEventListener'),
            removeEventListener: __cpaMaskNative(function() {}, 'removeEventListener'),
            dispatchEvent: __cpaMaskNative(function() { return true; }, 'dispatchEvent')
          };
          setTimeout(function() { if (dc.onopen) dc.onopen({ type: 'open' }); }, 20);
          return dc;
        }, 'createDataChannel');

        FakeRTCPeerConnection.prototype.createOffer = __cpaMaskNative(function(arg1) {
          var sdpStr = buildSpoofedSdp();
          var offer = new FakeRTCSessionDescription({ type: 'offer', sdp: sdpStr });
          if (typeof arg1 === 'function') {
            arg1(offer);
            return Promise.resolve(offer);
          }
          return Promise.resolve(offer);
        }, 'createOffer');

        FakeRTCPeerConnection.prototype.createAnswer = __cpaMaskNative(function(arg1) {
          var sdpStr = buildSpoofedSdp();
          var answer = new FakeRTCSessionDescription({ type: 'answer', sdp: sdpStr });
          if (typeof arg1 === 'function') {
            arg1(answer);
            return Promise.resolve(answer);
          }
          return Promise.resolve(answer);
        }, 'createAnswer');

        FakeRTCPeerConnection.prototype.setLocalDescription = __cpaMaskNative(function(desc, successCb) {
          var self = this;
          var ipNow = (window.__cpaTargetIp && ipv4Regex.test(window.__cpaTargetIp)) ? window.__cpaTargetIp : activeIp;
          var finalDesc = desc || new FakeRTCSessionDescription({ type: 'offer', sdp: buildSpoofedSdp() });
          self.localDescription = finalDesc;
          self.currentLocalDescription = finalDesc;
          self.signalingState = 'have-local-offer';
          self.dispatchEvent(new Event('signalingstatechange'));

          self.iceGatheringState = 'gathering';
          self.dispatchEvent(new Event('icegatheringstatechange'));

          // 1. Dispatch Host Candidate
          setTimeout(function() {
            if (self.signalingState === 'closed') return;
            var hostCandidate = new FakeRTCIceCandidate({
              candidate: 'candidate:842163049 1 udp 2122199295 ' + localIp + ' 54321 typ host generation 0 ufrag abcd network-id 1',
              sdpMid: '0',
              sdpMLineIndex: 0,
              type: 'host',
              address: localIp,
              port: 54321,
              relatedAddress: null,
              relatedPort: null
            });
            self.dispatchEvent({ type: 'icecandidate', candidate: hostCandidate, target: self, srcElement: self });
          }, 35);

          // 2. Dispatch Server-Reflexive Candidate containing the SPOOFED PROXY IP
          setTimeout(function() {
            if (self.signalingState === 'closed') return;
            var srflxCandidate = new FakeRTCIceCandidate({
              candidate: 'candidate:2130706431 1 udp 2122260223 ' + ipNow + ' 54322 typ srflx raddr ' + localIp + ' rport 54321 generation 0 ufrag abcd network-id 1',
              sdpMid: '0',
              sdpMLineIndex: 0,
              type: 'srflx',
              address: ipNow,
              port: 54322,
              relatedAddress: localIp,
              relatedPort: 54321
            });
            self.dispatchEvent({ type: 'icecandidate', candidate: srflxCandidate, target: self, srcElement: self });
          }, 85);

          // 3. Dispatch end-of-candidates (null candidate) and mark complete
          setTimeout(function() {
            if (self.signalingState === 'closed') return;
            self.dispatchEvent({ type: 'icecandidate', candidate: null, target: self, srcElement: self });
            self.iceGatheringState = 'complete';
            self.dispatchEvent(new Event('icegatheringstatechange'));
            self.iceConnectionState = 'checking';
            self.dispatchEvent(new Event('iceconnectionstatechange'));
          }, 150);

          if (typeof successCb === 'function') successCb();
          return Promise.resolve();
        }, 'setLocalDescription');

        FakeRTCPeerConnection.prototype.setRemoteDescription = __cpaMaskNative(function(desc, successCb) {
          this.remoteDescription = desc;
          this.currentRemoteDescription = desc;
          if (typeof successCb === 'function') successCb();
          return Promise.resolve();
        }, 'setRemoteDescription');

        FakeRTCPeerConnection.prototype.addIceCandidate = __cpaMaskNative(function(cand, successCb) {
          if (typeof successCb === 'function') successCb();
          return Promise.resolve();
        }, 'addIceCandidate');

        FakeRTCPeerConnection.prototype.getConfiguration = __cpaMaskNative(function() {
          return this.config || {};
        }, 'getConfiguration');

        FakeRTCPeerConnection.prototype.setConfiguration = __cpaMaskNative(function(config) {
          this.config = config || {};
        }, 'setConfiguration');

        FakeRTCPeerConnection.prototype.getSenders = __cpaMaskNative(function() { return []; }, 'getSenders');
        FakeRTCPeerConnection.prototype.getReceivers = __cpaMaskNative(function() { return []; }, 'getReceivers');
        FakeRTCPeerConnection.prototype.getTransceivers = __cpaMaskNative(function() { return []; }, 'getTransceivers');
        FakeRTCPeerConnection.prototype.addTrack = __cpaMaskNative(function(track) { return { track: track }; }, 'addTrack');
        FakeRTCPeerConnection.prototype.removeTrack = __cpaMaskNative(function() {}, 'removeTrack');
        FakeRTCPeerConnection.prototype.addTransceiver = __cpaMaskNative(function() { return {}; }, 'addTransceiver');

        FakeRTCPeerConnection.prototype.getStats = __cpaMaskNative(function(selector, successCb) {
          var ipNow = (window.__cpaTargetIp && ipv4Regex.test(window.__cpaTargetIp)) ? window.__cpaTargetIp : activeIp;
          var stats = new Map();
          stats.set('cand-pair', {
            type: 'candidate-pair',
            id: 'cand-pair',
            state: 'succeeded',
            localCandidateId: 'local-cand-srflx'
          });
          stats.set('local-cand-srflx', {
            type: 'local-candidate',
            id: 'local-cand-srflx',
            candidateType: 'srflx',
            ip: ipNow,
            address: ipNow,
            port: 54322,
            protocol: 'udp'
          });
          if (typeof selector === 'function') {
            selector(stats);
            return Promise.resolve(stats);
          }
          return Promise.resolve(stats);
        }, 'getStats');

        FakeRTCPeerConnection.prototype.close = __cpaMaskNative(function() {
          this.signalingState = 'closed';
          this.iceConnectionState = 'closed';
          this.iceGatheringState = 'complete';
          this.connectionState = 'closed';
        }, 'close');

        FakeRTCPeerConnection.generateCertificate = __cpaMaskNative(function() {
          return Promise.resolve({ expires: Date.now() + 86400000, getFingerprints: function() { return []; } });
        }, 'generateCertificate');

        try {
          Object.defineProperty(FakeRTCPeerConnection.prototype, Symbol.toStringTag, { value: 'RTCPeerConnection', configurable: true });
          Object.defineProperty(FakeRTCIceCandidate.prototype, Symbol.toStringTag, { value: 'RTCIceCandidate', configurable: true });
          Object.defineProperty(FakeRTCSessionDescription.prototype, Symbol.toStringTag, { value: 'RTCSessionDescription', configurable: true });
        } catch(e) {}

        targetWin.RTCPeerConnection = FakeRTCPeerConnection;
        targetWin.webkitRTCPeerConnection = FakeRTCPeerConnection;
        targetWin.mozRTCPeerConnection = FakeRTCPeerConnection;
        targetWin.msRTCPeerConnection = FakeRTCPeerConnection;
        targetWin.RTCIceCandidate = FakeRTCIceCandidate;
        targetWin.webkitRTCIceCandidate = FakeRTCIceCandidate;
        targetWin.RTCSessionDescription = FakeRTCSessionDescription;
        targetWin.webkitRTCSessionDescription = FakeRTCSessionDescription;

        try {
          Object.defineProperty(targetWin, 'RTCPeerConnection', { value: FakeRTCPeerConnection, writable: true, configurable: true });
          Object.defineProperty(targetWin, 'webkitRTCPeerConnection', { value: FakeRTCPeerConnection, writable: true, configurable: true });
          Object.defineProperty(targetWin, 'mozRTCPeerConnection', { value: FakeRTCPeerConnection, writable: true, configurable: true });
          Object.defineProperty(targetWin, 'msRTCPeerConnection', { value: FakeRTCPeerConnection, writable: true, configurable: true });
          Object.defineProperty(targetWin, 'RTCIceCandidate', { value: FakeRTCIceCandidate, writable: true, configurable: true });
          Object.defineProperty(targetWin, 'webkitRTCIceCandidate', { value: FakeRTCIceCandidate, writable: true, configurable: true });
          Object.defineProperty(targetWin, 'RTCSessionDescription', { value: FakeRTCSessionDescription, writable: true, configurable: true });
          Object.defineProperty(targetWin, 'webkitRTCSessionDescription', { value: FakeRTCSessionDescription, writable: true, configurable: true });
        } catch(e) {}

        // Codecs & Capabilities spoofing for BrowserLeaks
        var sampleAudioCodecs = [
          { mimeType: 'audio/opus', clockRate: 48000, channels: 2, sdpFmtpLine: 'minptime=10;useinbandfec=1' },
          { mimeType: 'audio/PCMU', clockRate: 8000, channels: 1 },
          { mimeType: 'audio/PCMA', clockRate: 8000, channels: 1 }
        ];
        var sampleVideoCodecs = [
          { mimeType: 'video/VP8', clockRate: 90000 },
          { mimeType: 'video/H264', clockRate: 90000, sdpFmtpLine: 'level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42e01f' },
          { mimeType: 'video/VP9', clockRate: 90000, sdpFmtpLine: 'profile-id=0' }
        ];
        targetWin.RTCRtpSender = {
          getCapabilities: __cpaMaskNative(function(k) {
            return { codecs: k === 'audio' ? sampleAudioCodecs : sampleVideoCodecs, headerExtensions: [] };
          }, 'getCapabilities')
        };
        targetWin.RTCRtpReceiver = {
          getCapabilities: __cpaMaskNative(function(k) {
            return { codecs: k === 'audio' ? sampleAudioCodecs : sampleVideoCodecs, headerExtensions: [] };
          }, 'getCapabilities')
        };
      } catch(e) {}
    }

    function applyFullContextToWindow(targetWin) {
      if (!targetWin) return;
      try {
        applyWebRtcToWindow(targetWin);
        if (targetWin !== window && targetWin.navigator) {
          var subNavProto = (targetWin.Navigator && targetWin.Navigator.prototype) ? targetWin.Navigator.prototype : targetWin.navigator;
          defineProtoGetter(subNavProto, targetWin.navigator, 'webdriver', function() { return false; });
          defineProtoGetter(subNavProto, targetWin.navigator, 'platform', function() { return __platformStr; });
          defineProtoGetter(subNavProto, targetWin.navigator, 'hardwareConcurrency', function() { return __hwCores; });
          defineProtoGetter(subNavProto, targetWin.navigator, 'deviceMemory', function() { return __hwMem; });
          defineProtoGetter(subNavProto, targetWin.navigator, 'maxTouchPoints', function() { return __touchPts; });
          try {
            if (!__isMobile) {
              try { delete targetWin.ontouchstart; } catch(e) {}
            } else if (!('ontouchstart' in targetWin)) {
              try { Object.defineProperty(targetWin, 'ontouchstart', { value: null, writable: true, enumerable: true, configurable: true }); } catch(e) {}
            }
          } catch(e) {}
          defineProtoGetter(subNavProto, targetWin.navigator, 'language', function() { return window.__cpaTargetLang || '$cleanLang'; });
          defineProtoGetter(subNavProto, targetWin.navigator, 'languages', function() { var l = window.__cpaTargetLang || '$cleanLang'; return [l, l.split('-')[0]]; });
          if (__rawUa.length > 0 && __rawUa.toLowerCase() !== 'random') {
            defineProtoGetter(subNavProto, targetWin.navigator, 'userAgent', function() { return __rawUa; });
            defineProtoGetter(subNavProto, targetWin.navigator, 'appVersion', function() { return __rawUa.replace(/^Mozilla\//, ''); });
          }
          if (targetWin.Date && targetWin.Date.prototype && Date.prototype.getTimezoneOffset) {
            targetWin.Date.prototype.getTimezoneOffset = Date.prototype.getTimezoneOffset;
          }
          if (targetWin.Intl && Intl.DateTimeFormat) {
            targetWin.Intl.DateTimeFormat = Intl.DateTimeFormat;
          }
        }
      } catch(e) {}
    }

    // Apply WebRTC spoof to top window immediately
    applyWebRtcToWindow(window);

    // Synchronous Iframe WebRTC & Fingerprint Shield (prevents <iframe src="about:blank"> synchronous leak)
    if (!window.__cpaIframeShieldInstalled) {
      Object.defineProperty(window, '__cpaIframeShieldInstalled', { value: true, enumerable: false, configurable: true });
      try {
        function patchNodeAndDescendants(node) {
          try {
            if (!node) return;
            if (node.tagName === 'IFRAME') {
              try { if (node.contentWindow) applyFullContextToWindow(node.contentWindow); } catch(e) {}
              node.addEventListener('load', function() {
                try { if (node.contentWindow) applyFullContextToWindow(node.contentWindow); } catch(e) {}
              });
            } else if (node.getElementsByTagName) {
              var nested = node.getElementsByTagName('iframe');
              for (var i = 0; i < nested.length; i++) {
                var ifr = nested[i];
                try { if (ifr.contentWindow) applyFullContextToWindow(ifr.contentWindow); } catch(e) {}
                ifr.addEventListener('load', (function(f) {
                  return function() { try { if (f.contentWindow) applyFullContextToWindow(f.contentWindow); } catch(e) {} };
                })(ifr));
              }
            }
          } catch(e) {}
        }

        if (window.HTMLIFrameElement && HTMLIFrameElement.prototype) {
          var cwDesc = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'contentWindow');
          if (cwDesc && typeof cwDesc.get === 'function') {
            var origCwGet = cwDesc.get;
            Object.defineProperty(HTMLIFrameElement.prototype, 'contentWindow', {
              get: __cpaMaskNative(function() {
                var win = origCwGet.call(this);
                try { if (win) applyFullContextToWindow(win); } catch(e) {}
                return win;
              }, 'get contentWindow'),
              configurable: true,
              enumerable: true
            });
          }
        }

        var origAppendChild = Node.prototype.appendChild;
        Node.prototype.appendChild = __cpaMaskNative(function(el) {
          var res = origAppendChild.apply(this, arguments);
          patchNodeAndDescendants(el);
          return res;
        }, 'appendChild');

        var origInsertBefore = Node.prototype.insertBefore;
        Node.prototype.insertBefore = __cpaMaskNative(function(newNode, referenceNode) {
          var res = origInsertBefore.apply(this, arguments);
          patchNodeAndDescendants(newNode);
          return res;
        }, 'insertBefore');

        if (Element.prototype.append) {
          var origAppend = Element.prototype.append;
          Element.prototype.append = __cpaMaskNative(function() {
            var res = origAppend.apply(this, arguments);
            for (var i = 0; i < arguments.length; i++) patchNodeAndDescendants(arguments[i]);
            return res;
          }, 'append');
        }
        if (Element.prototype.prepend) {
          var origPrepend = Element.prototype.prepend;
          Element.prototype.prepend = __cpaMaskNative(function() {
            var res = origPrepend.apply(this, arguments);
            for (var i = 0; i < arguments.length; i++) patchNodeAndDescendants(arguments[i]);
            return res;
          }, 'prepend');
        }
        if (Element.prototype.insertAdjacentElement) {
          var origInsertAdjEl = Element.prototype.insertAdjacentElement;
          Element.prototype.insertAdjacentElement = __cpaMaskNative(function(pos, el) {
            var res = origInsertAdjEl.apply(this, arguments);
            patchNodeAndDescendants(el);
            return res;
          }, 'insertAdjacentElement');
        }
        if (Element.prototype.insertAdjacentHTML) {
          var origInsertAdjHtml = Element.prototype.insertAdjacentHTML;
          Element.prototype.insertAdjacentHTML = __cpaMaskNative(function() {
            var res = origInsertAdjHtml.apply(this, arguments);
            patchNodeAndDescendants(this);
            return res;
          }, 'insertAdjacentHTML');
        }

        setInterval(function() {
          try {
            var iframes = document.getElementsByTagName('iframe');
            for (var i = 0; i < iframes.length; i++) {
              var ifr = iframes[i];
              if (ifr && ifr.contentWindow) {
                applyFullContextToWindow(ifr.contentWindow);
              }
            }
          } catch(e) {}
        }, 500);
      } catch(e) {}
    }

    // 6. Web Worker & SharedWorker Consistency Shield (Prevents Worker-vs-Window mismatch detection)
    if (!window.__cpaWorkerShieldInstalled) {
      Object.defineProperty(window, '__cpaWorkerShieldInstalled', { value: true, enumerable: false, configurable: true });
      try {
        var buildWorkerPrelude = function() {
          var wTz = window.__cpaTargetTz || '$cleanTz';
          var wLang = window.__cpaTargetLang || '$cleanLang';
          return "(function(){try{" +
            "var navP=typeof WorkerNavigator!=='undefined'?WorkerNavigator.prototype:navigator;" +
            "Object.defineProperty(navP,'webdriver',{get:function(){return false;},configurable:true});" +
            "Object.defineProperty(navP,'platform',{get:function(){return '" + __platformStr + "';},configurable:true});" +
            "Object.defineProperty(navP,'hardwareConcurrency',{get:function(){return 8;},configurable:true});" +
            "Object.defineProperty(navP,'deviceMemory',{get:function(){return 8;},configurable:true});" +
            "Object.defineProperty(navP,'language',{get:function(){return '" + wLang + "';},configurable:true});" +
            "Object.defineProperty(navP,'languages',{get:function(){return ['" + wLang + "','" + wLang.split('-')[0] + "'];},configurable:true});" +
            (__rawUa.length > 0 && __rawUa.toLowerCase() !== 'random' ? "Object.defineProperty(navP,'userAgent',{get:function(){return '" + __rawUa.replace(/'/g, "\\'") + "';},configurable:true});" : "") +
            "if(typeof Intl!=='undefined'&&Intl.DateTimeFormat){var ODTF=Intl.DateTimeFormat;var oRes=ODTF.prototype.resolvedOptions;ODTF.prototype.resolvedOptions=function(){var r=oRes.call(this);try{r.timeZone='" + wTz + "';}catch(e){}return r;};}" +
            "delete self.RTCPeerConnection;delete self.webkitRTCPeerConnection;" +
          "}catch(e){}})();\n";
        };
        if (typeof window.Worker === 'function') {
          var OrigWorker = window.Worker;
          window.Worker = __cpaMaskNative(function(scriptURL, options) {
            try {
              var urlStr = String(scriptURL || '');
              if (urlStr.indexOf('blob:') === 0 || urlStr.indexOf('data:') === 0) {
                var prelude = buildWorkerPrelude();
                var wrapperCode = prelude + 'importScripts(' + JSON.stringify(urlStr) + ');';
                var blob = new Blob([wrapperCode], { type: 'application/javascript' });
                return new OrigWorker(URL.createObjectURL(blob), options);
              }
            } catch(e) {}
            return new OrigWorker(scriptURL, options);
          }, 'Worker');
          window.Worker.prototype = OrigWorker.prototype;
        }
      } catch(e) {}
    }

    // 7. JS-Level Local Network, Loopback & STUN/TURN Port-Scan Firewall
    if (!window.__cpaNetFirewallInstalled) {
      Object.defineProperty(window, '__cpaNetFirewallInstalled', { value: true, enumerable: false, configurable: true });
      try {
        var isBlockedProbeUrl = function(rawUrl) {
          try {
            if (!rawUrl) return false;
            var str = String(rawUrl).trim().toLowerCase();
            if (str.indexOf('stun:') === 0 || str.indexOf('stuns:') === 0 || str.indexOf('turn:') === 0 || str.indexOf('turns:') === 0) return true;
            var u = new URL(str, window.location.href);
            var h = (u.hostname || '').toLowerCase();
            var p = parseInt(u.port, 10) || 0;
            if (p === 3478 || p === 5349 || p === 19302 || p === 19305 || p === 19307 || p === 19308) return true;
            if (h === 'localhost' || h.slice(-6) === '.local' || h === '127.0.0.1' || h === '0.0.0.0' || h === '[::1]' || h === '::1') return true;
            if (h.indexOf('127.') === 0 || h.indexOf('10.') === 0 || h.indexOf('192.168.') === 0 || h.indexOf('169.254.') === 0) return true;
            if (/^172\.(1[6-9]|2\d|3[0-1])\./.test(h)) return true;
            if (h.indexOf('stun.') === 0 || h.indexOf('turn.') === 0 || h.indexOf('.stun.') !== -1 || h.indexOf('.turn.') !== -1) return true;
          } catch(e) {}
          return false;
        };

        if (typeof window.fetch === 'function') {
          var origFetch = window.fetch;
          window.fetch = __cpaMaskNative(function(input, init) {
            var urlStr = (typeof input === 'string') ? input : (input && input.url ? input.url : '');
            if (isBlockedProbeUrl(urlStr)) {
              return Promise.reject(new TypeError('Failed to fetch'));
            }
            return origFetch.apply(this, arguments);
          }, 'fetch');
        }

        if (window.XMLHttpRequest && XMLHttpRequest.prototype) {
          var origXhrOpen = XMLHttpRequest.prototype.open;
          var origXhrSend = XMLHttpRequest.prototype.send;
          XMLHttpRequest.prototype.open = __cpaMaskNative(function(method, url) {
            if (isBlockedProbeUrl(url)) {
              Object.defineProperty(this, '__cpaBlockedProbe', { value: true, enumerable: false, configurable: true, writable: true });
            }
            return origXhrOpen.apply(this, arguments);
          }, 'open');
          XMLHttpRequest.prototype.send = __cpaMaskNative(function() {
            if (this.__cpaBlockedProbe) {
              var self = this;
              setTimeout(function() {
                try { self.dispatchEvent(new Event('error')); } catch(e) {}
              }, 10);
              return;
            }
            return origXhrSend.apply(this, arguments);
          }, 'send');
        }

        if (typeof window.WebSocket === 'function') {
          var OrigWS = window.WebSocket;
          window.WebSocket = __cpaMaskNative(function(url, protocols) {
            if (isBlockedProbeUrl(url)) {
              throw new DOMException("Failed to construct 'WebSocket': The URL '" + url + "' is invalid.", 'SyntaxError');
            }
            return protocols !== undefined ? new OrigWS(url, protocols) : new OrigWS(url);
          }, 'WebSocket');
          window.WebSocket.prototype = OrigWS.prototype;
          window.WebSocket.CONNECTING = OrigWS.CONNECTING;
          window.WebSocket.OPEN = OrigWS.OPEN;
          window.WebSocket.CLOSING = OrigWS.CLOSING;
          window.WebSocket.CLOSED = OrigWS.CLOSED;
        }

        if (navigator.sendBeacon) {
          var origBeacon = navigator.sendBeacon;
          navigator.sendBeacon = __cpaMaskNative(function(url, data) {
            if (isBlockedProbeUrl(url)) return false;
            return origBeacon.apply(this, arguments);
          }, 'sendBeacon');
        }
      } catch(e) {}
    }

    // Media devices spoofing
    if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
      navigator.mediaDevices.enumerateDevices = __cpaMaskNative(function() {
        return Promise.resolve([
          { deviceId: 'default', kind: 'audioinput', label: '', groupId: 'audio-1', toJSON: __cpaMaskNative(function() { return this; }, 'toJSON') },
          { deviceId: 'default', kind: 'audiooutput', label: '', groupId: 'audio-1', toJSON: __cpaMaskNative(function() { return this; }, 'toJSON') },
          { deviceId: 'cam1', kind: 'videoinput', label: '', groupId: 'video-1', toJSON: __cpaMaskNative(function() { return this; }, 'toJSON') }
        ]);
      }, 'enumerateDevices');
    }
    if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
      navigator.mediaDevices.getUserMedia = __cpaMaskNative(function() {
        return Promise.reject(new DOMException('Permission denied', 'NotAllowedError'));
      }, 'getUserMedia');
    }
    if (navigator.mediaDevices && navigator.mediaDevices.getSupportedConstraints) {
      navigator.mediaDevices.getSupportedConstraints = __cpaMaskNative(function() {
        return {
          aspectRatio: true,
          autoGainControl: true,
          channelCount: true,
          deviceId: true,
          echoCancellation: true,
          facingMode: true,
          frameRate: true,
          groupId: true,
          height: true,
          noiseSuppression: true,
          sampleRate: true,
          sampleSize: true,
          width: true
        };
      }, 'getSupportedConstraints');
    }
  } catch(e) {}
})();
true;
        """.trimIndent()
    }

    /**
     * Tracker Deception — خداع المتتبعات: stubs مزيفة لواجهات hotjar وأخواتها
     * حتى تظن الصفحة أن التتبع يعمل (لا جدار كشف مانع إعلانات) بينما الشبكة
     * محظورة أصلاً — حظر غير مرئي ينجّح التحويلة دون كشف.
     */
    fun buildTrackerDeceptionScript(): String {
        return """
(function() {
  if (window.__cpaTrackerDeceived) return;
  try {
    Object.defineProperty(window, '__cpaTrackerDeceived', { value: true, enumerable: false, configurable: true });
  } catch(e) {}
  function maskFn(fn, name) {
    try {
      if (window.__cpaToStringMap && typeof fn === 'function') {
        try { Object.defineProperty(fn, 'name', { value: name || '', configurable: true }); } catch(e) {}
        window.__cpaToStringMap.set(fn, 'function ' + (name || '') + '() { [native code] }');
      }
    } catch(e) {}
    return fn;
  }
  // جامع صامت: يقبل أي استدعاء ويبتلعه (كأن الإرسال نجح)
  function queueStub() {
    var q = [];
    var f = function() { q.push(Array.prototype.slice.call(arguments)); return undefined; };
    try {
      f.q = q;
      f.push = function() { q.push(Array.prototype.slice.call(arguments)); };
      f.loaded = true;
      f.version = '3.1.0';
    } catch(e) {}
    return f;
  }
  function ensureFn(name, factory) {
    try { if (typeof window[name] === 'undefined') window[name] = factory(); } catch(e) {}
  }
  function ensureObj(name, factory) {
    try { if (typeof window[name] === 'undefined') window[name] = factory(); } catch(e) {}
  }
  // Hotjar
  ensureFn('hj', queueStub);
  ensureObj('_hjSettings', function() { return { hjid: 0, hjsv: 6 }; });
  // FullStory
  ensureObj('FS', function() {
    return {
      identify: function() {}, setUserVars: function() {}, log: function() {}, event: function() {},
      shutdown: function() {}, restart: function() {}, consent: function() {}, anonymize: function() {},
      getSession: function() { return { getSessionId: function() { return 'cpa-fake-session'; }, getSessionUrl: function() { return ''; } }; },
      getSessionId: function() { return 'cpa-fake-session'; }
    };
  });
  ensureFn('_fs_namespace', function() { return 'FS'; });
  // Mouseflow / CrazyEgg / Inspectlet / LuckyOrange / Smartlook / LogRocket
  ensureFn('mouseflow', queueStub);
  ensureObj('_mfq', function() { return []; });
  ensureFn('CE2', queueStub);
  ensureFn('__insp', queueStub);
  ensureObj('__inspld', function() { return true; });
  ensureFn('LO', queueStub);
  ensureObj('__lo_site_id', function() { return 0; });
  ensureFn('smartlook', queueStub);
  ensureObj('Smartlook', function() { return { init: function() {}, record: function() {}, pause: function() {}, stop: function() {} }; });
  ensureObj('LogRocket', function() {
    return { init: function() {}, identify: function() {}, track: function() {}, log: function() {}, version: '8.0.0', sessionURL: '' };
  });
  // Contentsquare / QuantumMetric / SessionCam / UserReplay / Glassbox / Decibel
  ensureFn('_uxa', queueStub);
  ensureFn('QuantumMetric', queueStub);
  ensureFn('sessioncam', queueStub);
  ensureFn('userreplay', queueStub);
  ensureObj('_glassbox', function() { return { sendEvent: function() {}, setUserId: function() {} }; });
  ensureFn('_dbs', queueStub);
  ensureObj('clicktale', function() { return { field: function() {}, record: function() {} }; });
  return 'deceived_v1';
})();
        """.trimIndent()
    }

    /**
     * Device Bridge — الجسر الكامل: يفهم كل طلب وصول للجهاز ويحوّله للمزيف.
     * نواة تصنيف مركزية (__cpaBridge): تحصي لمسات البصمة، تسجّل النتيجة، وتبلّغ
     * الأصلي (AndroidBridge.onShieldTelemetry) عند رصد فحص بصمة حقيقي.
     * يغطي: الذاكرة/التخزين/الخطوط/الاتجاه/matchMedia/الصوت + حقن Workers.
     */
    fun buildDeviceBridgeScript(
        latitude: Double = 40.7128,
        longitude: Double = -74.0060,
        userAgent: String = "",
        fingerprintSeed: Long = 0L,
        language: String = "en-US"
    ): String {
        val cleanUa = userAgent.trim().replace("'", "\\'")
        val seedInit = if (fingerprintSeed == 0L) "2166136261" else "${fingerprintSeed}>>>0"
        val cleanBridgeLang = language.trim().replace("'", "").takeIf {
            it.matches(Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?$"))
        } ?: "en-US"
        val uaLowerBridge = cleanUa.lowercase()
        val bridgePlatform = when {
            uaLowerBridge.contains("android") -> "Linux armv8l"
            uaLowerBridge.contains("iphone") || uaLowerBridge.contains("ipad") -> "iPhone"
            !uaLowerBridge.contains("iphone") && uaLowerBridge.contains("macintosh") -> "MacIntel"
            !uaLowerBridge.contains("android") && uaLowerBridge.contains("linux") -> "Linux x86_64"
            else -> "Win32"
        }
        return """
(function() {
  if (window.__cpaBridge && window.__cpaBridge.v === 1) return;
  var SEED = (window.__cpaSessionSeed || ($seedInit)) >>> 0;
  var RUA = ('$cleanUa' || (typeof navigator !== 'undefined' ? navigator.userAgent : '') || '').toLowerCase();
  var MOBILE = RUA.indexOf('android') !== -1 || RUA.indexOf('iphone') !== -1 || RUA.indexOf('mobile') !== -1;
  // نفس ملف الشاشة في الدرع الرئيسي (نفس البذرة + نفس الملح = نفس القيم)
  var __bIsIPhone = RUA.indexOf('iphone') !== -1 || RUA.indexOf('ipad') !== -1;
  var __bIsAndroid = RUA.indexOf('android') !== -1;
  var __bDpr = __bIsAndroid ? [2.625, 2.5, 2, 2.25, 2.625][H(104) % 5] : (__bIsIPhone ? 3 : [1, 1, 1, 1.25, 1, 1, 1][H(104) % 7]);
  var __bScrW = __bIsAndroid ? [412, 390, 360, 384, 412][H(104) % 5] : (__bIsIPhone ? 390 : [1920, 1920, 1366, 1536, 1440, 1600, 2560][H(104) % 7]);
  var __bScrH = __bIsAndroid ? [915, 844, 800, 854, 892][H(104) % 5] : (__bIsIPhone ? 844 : [1080, 1080, 768, 864, 900, 900, 1440][H(104) % 7]);
  function H(n) { var x = ((n ^ SEED) + 0x9e3779b9) >>> 0; x = Math.imul(x ^ (x >>> 16), 0x85ebca6b) >>> 0; x = Math.imul(x ^ (x >>> 13), 0xc2b2ae35) >>> 0; return (x ^ (x >>> 16)) >>> 0; }
  function maskFn(fn, name) {
    try {
      if (window.__cpaToStringMap && typeof fn === 'function') {
        try { Object.defineProperty(fn, 'name', { value: name || '', configurable: true }); } catch(e) {}
        window.__cpaToStringMap.set(fn, 'function ' + (name || '') + '() { [native code] }');
      }
    } catch(e) {}
    return fn;
  }

  // ── نواة الفهم: تصنيف كل طلب + نتيجة خطورة ──
  var WEIGHTS = { canvas: 3, webgl: 3, webgpu: 4, audio: 2, font: 2, rtc: 4, geo: 4, camera: 5, worker: 2, storage: 1, perf: 1, media: 2 };
  var hits = {};
  var lastReport = 0;
  function scoreNow() {
    var s = 0;
    for (var k in hits) { if (WEIGHTS[k]) s += Math.min(hits[k], 5) * WEIGHTS[k]; }
    return s;
  }
  function note(api) {
    hits[api] = (hits[api] || 0) + 1;
    var score = scoreNow();
    var now = Date.now();
    if ((score >= 12 || api === 'camera' || api === 'geo') && (now - lastReport > 10000)) {
      lastReport = now;
      try {
        var arr = [];
        for (var k2 in hits) arr.push(k2 + ':' + hits[k2]);
        arr.sort(function(a, b) { return parseInt(b.split(':')[1], 10) - parseInt(a.split(':')[1], 10); });
        var payload = JSON.stringify({ u: (window.location.href || '').slice(0, 120), score: score, top: arr.slice(0, 4) });
        if (window.AndroidBridge && window.AndroidBridge.onShieldTelemetry) window.AndroidBridge.onShieldTelemetry(payload);
      } catch(e) {}
    }
    return score;
  }
  function countWrap(obj, name, api) {
    try {
      if (!obj) return;
      var orig = obj[name];
      if (typeof orig !== 'function' || orig.__cpaBridged) return;
      var w = function() { note(api); return orig.apply(this, arguments); };
      w.__cpaBridged = true;
      obj[name] = maskFn(w, name);
    } catch(e) {}
  }

  // ── عدّادات الفهم على واجهات البصمة (القيم يضبطها الدرع الرئيسي، الجسر يفهم ويحصي) ──
  try {
    if (window.CanvasRenderingContext2D) {
      countWrap(CanvasRenderingContext2D.prototype, 'getImageData', 'canvas');
      // بصمة الخطوط: إزاحة مجهرية ثابتة لكل (خط+نص) — تهزم القياس دون كسر التخطيط
      var origMeasure = CanvasRenderingContext2D.prototype.measureText;
      if (typeof origMeasure === 'function' && !origMeasure.__cpaBridged) {
        var wMeasure = function(txt) {
          note('font');
          var m = origMeasure.apply(this, arguments);
          try {
            var key = (this.font || '') + '|' + String(txt);
            var hh = 0;
            for (var i = 0; i < key.length; i++) hh = ((hh * 31) + key.charCodeAt(i)) >>> 0;
            hh = H(hh);
            var dw = (((hh % 200) / 200) - 0.5) * 0.24;
            if (dw !== 0) {
              var out = {};
              for (var p in m) { try { out[p] = m[p]; } catch(e) {} }
              out.width = m.width + dw;
              return out;
            }
          } catch(e) {}
          return m;
        };
        wMeasure.__cpaBridged = true;
        CanvasRenderingContext2D.prototype.measureText = maskFn(wMeasure, 'measureText');
      }
    }
    if (window.HTMLCanvasElement) {
      countWrap(HTMLCanvasElement.prototype, 'toDataURL', 'canvas');
      countWrap(HTMLCanvasElement.prototype, 'toBlob', 'canvas');
    }
    if (window.WebGLRenderingContext) {
      countWrap(WebGLRenderingContext.prototype, 'getParameter', 'webgl');
      countWrap(WebGLRenderingContext.prototype, 'readPixels', 'webgl');
    }
    if (window.WebGL2RenderingContext) {
      countWrap(WebGL2RenderingContext.prototype, 'getParameter', 'webgl');
      countWrap(WebGL2RenderingContext.prototype, 'readPixels', 'webgl');
    }
    if (window.OfflineAudioContext) countWrap(OfflineAudioContext.prototype, 'startRendering', 'audio');
    if (window.AnalyserNode) {
      countWrap(AnalyserNode.prototype, 'getByteFrequencyData', 'audio');
      countWrap(AnalyserNode.prototype, 'getFloatFrequencyData', 'audio');
    }
    if (navigator.geolocation) {
      var gp = Object.getPrototypeOf(navigator.geolocation) || navigator.geolocation;
      countWrap(gp, 'getCurrentPosition', 'geo');
      countWrap(gp, 'watchPosition', 'geo');
    }
    if (navigator.mediaDevices) {
      countWrap(navigator.mediaDevices, 'getUserMedia', 'camera');
      countWrap(navigator.mediaDevices, 'enumerateDevices', 'media');
    }
    if (window.RTCPeerConnection && !window.RTCPeerConnection.__cpaBridged) {
      try {
        var OrigRTC = window.RTCPeerConnection;
        var WRTC = function() { note('rtc'); return new OrigRTC(arguments[0]); };
        WRTC.__cpaBridged = true;
        WRTC.prototype = OrigRTC.prototype;
        window.RTCPeerConnection = maskFn(WRTC, 'RTCPeerConnection');
      } catch(e) {}
    }
    if (navigator.storage && navigator.storage.estimate) countWrap(navigator.storage, 'estimate', 'storage');
  } catch(e) {}

  // ── تحويلات الجسر: ذاكرة الأداء + حصة التخزين (مطابقة تماماً ل pools الدرع الرئيسي) ──
  var HW_MEM = MOBILE ? [4, 4, 8][H(102) % 3] : [4, 8, 8][H(102) % 3];
  try {
    if (window.performance && window.performance.memory) {
      var heapLimit = HW_MEM * 1073741824;
      var heapUsed = 14000000 + (H(201) % 26000000);
      Object.defineProperty(window.performance, 'memory', {
        get: maskFn(function() {
          note('perf');
          return { usedJSHeapSize: heapUsed, totalJSHeapSize: Math.floor(heapUsed * 1.6), jsHeapSizeLimit: heapLimit };
        }, 'get memory'),
        configurable: true
      });
    }
  } catch(e) {}
  try {
    if (navigator.storage && navigator.storage.estimate) {
      var quota = MOBILE ? (6442450944 + (H(202) % 1000000000)) : (107374182400 + (H(202) % 10000000000));
      var usage = 12000000 + (H(203) % 60000000);
      var origEst = navigator.storage.estimate.bind(navigator.storage);
      navigator.storage.estimate = maskFn(function() {
        note('storage');
        return Promise.resolve({ quota: quota, usage: usage, usageDetails: {} });
      }, 'estimate');
    }
  } catch(e) {}

  // ── اتساق الاتجاه والمؤشر مع فئة الجهاز ──
  try {
    Object.defineProperty(screen, 'orientation', {
      get: maskFn(function() {
        return { angle: 0, type: MOBILE ? 'portrait-primary' : 'landscape-primary', onchange: null };
      }, 'get orientation'),
      configurable: true
    });
  } catch(e) {}
  try {
    var origMatchMedia = window.matchMedia;
    if (typeof origMatchMedia === 'function' && !origMatchMedia.__cpaBridged) {
      var wMM = function(query) {
        var q = String(query || '');
        var noop = function() {};
        var fakeMQL = function(matches) {
          return { matches: !!matches, media: q, onchange: null, addListener: noop, removeListener: noop, addEventListener: noop, removeEventListener: noop, dispatchEvent: function() { return true; } };
        };
        // Color scheme fixed to light (consistent session-wide; system dark mode would contradict)
        if (q.indexOf('prefers-color-scheme') !== -1) {
          return fakeMQL(q.indexOf('light') !== -1);
        }
        // Resolution + device dims must agree with the spoofed screen (CSS sees real values otherwise)
        if (q.indexOf('resolution') !== -1 || q.indexOf('dppx') !== -1 || q.indexOf('pixel-ratio') !== -1 || (q.indexOf('dpi') !== -1 && q.indexOf('resolution') === -1 && q.indexOf('scan') === -1)) {
          try {
            var m = q.match(/(\d+(?:\.\d+)?)\s*(dppx|dpi|x)?/);
            if (m) {
              var v = parseFloat(m[1]);
              var unit = m[2] || 'x';
              var actual = (unit === 'dpi') ? (__bDpr * 96) : __bDpr;
              var isMin = q.indexOf('min-') !== -1;
              var isMax = q.indexOf('max-') !== -1;
              var ok = isMin ? (actual >= v - 0.001) : (isMax ? (actual <= v + 0.001) : (Math.abs(actual - v) < 0.01));
              return fakeMQL(ok);
            }
          } catch(e) {}
        }
        if (q.indexOf('device-width') !== -1 || q.indexOf('device-height') !== -1) {
          try {
            var dm = q.match(/(\d+(?:\.\d+)?)\s*(px)?/);
            if (dm) {
              var dv = parseFloat(dm[1]);
              var dActual = (q.indexOf('device-height') !== -1) ? __bScrH : __bScrW;
              var dMin = q.indexOf('min-') !== -1;
              var dMax = q.indexOf('max-') !== -1;
              var dOk = dMin ? (dActual >= dv) : (dMax ? (dActual <= dv) : (dActual === dv));
              return fakeMQL(dOk);
            }
          } catch(e) {}
        }
        if (q.indexOf('pointer') !== -1 || q.indexOf('hover') !== -1) {
          var wantFine = q.indexOf('fine') !== -1 || (q.indexOf('hover') !== -1 && q.indexOf('hover: hover') !== -1);
          var wantCoarse = q.indexOf('coarse') !== -1 || (q.indexOf('hover') !== -1 && q.indexOf('none') !== -1);
          var matches = MOBILE ? (wantCoarse || (!wantFine && q.indexOf('none') !== -1)) : (wantFine || (!wantCoarse && q.indexOf('hover') !== -1));
          if (q.indexOf('any-pointer') !== -1 || q.indexOf('any-hover') !== -1) matches = true;
          return fakeMQL(matches);
        }
        return origMatchMedia.apply(this, arguments);
      };
      wMM.__cpaBridged = true;
      window.matchMedia = maskFn(wMM, 'matchMedia');
    }
  } catch(e) {}

  // ── حركة الجهاز (موبايل فقط): حدث واحد لطيف "هاتف على الطاولة" عند أول مستمع ──
  try {
    if (MOBILE && typeof window.DeviceOrientationEvent !== 'undefined') {
      var __cpaOriFired = false;
      var origAddEv = window.addEventListener;
      window.addEventListener = maskFn(function(type, listener, opts) {
        try {
          if ((type === 'deviceorientation' || type === 'devicemotion') && !__cpaOriFired) {
            __cpaOriFired = true;
            setTimeout(function() {
              try {
                if (type === 'deviceorientation') {
                  window.dispatchEvent(new DeviceOrientationEvent('deviceorientation', {
                    alpha: H(204) % 360, beta: 8 + (H(205) % 10), gamma: (H(206) % 7) - 3, absolute: false
                  }));
                }
              } catch(e) {}
            }, 900);
          }
        } catch(e) {}
        return origAddEv.call(this, type, listener, opts);
      }, 'addEventListener');
    }
  } catch(e) {}

  // ── درع Workers: إعادة كتابة Blob workers مع درع مدمج + تمرير الأحداث ──
  window.__cpaWorkerShield =
    '(function(){try{' +
    'var __ws=' + SEED + ';' +
    'function __wh(n){var x=((n^__ws)+0x9e3779b9)>>>0;x=Math.imul(x^(x>>>16),0x85ebca6b)>>>0;x=Math.imul(x^(x>>>13),0xc2b2ae35)>>>0;return (x^(x>>>16))>>>0;}' +
    'var __wm=' + (MOBILE ? 'true' : 'false') + ';' +
    'var __wua="' + cleanUa.replace("\"", "") + '";' +
    'var __wplat="' + bridgePlatform + '";' +
    'var __wlang="' + cleanBridgeLang + '";' +
    'var __wc=__wm?[4,6,8,8][__wh(101)%4]:[4,6,8,8,12,16][__wh(101)%6];' +
    'var __wd=__wm?[4,4,8][__wh(102)%3]:[4,8,8][__wh(102)%3];' +
    'var __wbase="' + (tryBase()) + '";' +
    'try{var __ob=importScripts;importScripts=function(){var a=[];for(var i=0;i<arguments.length;i++){var u=arguments[i];try{if(u.indexOf("://")<0&&u.indexOf("blob:")!==0){u=__wbase+u;}}catch(e){}a.push(u);}return __ob.apply(this,a);};}catch(e){}' +
    'try{Object.defineProperty(self.navigator,"hardwareConcurrency",{get:function(){return __wc;},configurable:true});}catch(e){}' +
    'try{Object.defineProperty(self.navigator,"deviceMemory",{get:function(){return __wd;},configurable:true});}catch(e){}' +
    'try{Object.defineProperty(self.navigator,"webdriver",{get:function(){return false;},configurable:true});}catch(e){}' +
    'try{Object.defineProperty(self.navigator,"platform",{get:function(){return __wplat;},configurable:true});}catch(e){}' +
    'try{Object.defineProperty(self.navigator,"userAgent",{get:function(){return __wua;},configurable:true});}catch(e){}' +
    'try{Object.defineProperty(self.navigator,"languages",{get:function(){return [__wlang,__wlang.split("-")[0]];},configurable:true});}catch(e){}' +
    '}catch(e){}})();';
  function tryBase() {
    try {
      var h = window.location.href || 'https://localhost/';
      return h.replace(/[^\/]*$/, '');
    } catch(e) { return 'https://localhost/'; }
  }
  try {
    var OrigWorker = window.Worker;
    if (typeof OrigWorker === 'function' && !OrigWorker.__cpaBridged) {
      var CpaWorker = function(url, opts) {
        note('worker');
        var s = '';
        try { s = String(url); } catch(e) { return new OrigWorker(url, opts); }
        if (s.indexOf('blob:') !== 0) return new OrigWorker(url, opts);
        // واجهة مطابقة تمرر كل شيء للعامل الحقيقي بعد حقن الدرع
        var facade = {};
        var queue = [];
        var real = null;
        var dead = false;
        var listeners = {};
        function emit(type, ev) {
          try {
            var prop = facade['on' + type];
            if (typeof prop === 'function') { try { prop.call(facade, ev); } catch(e) {} }
            var arr = listeners[type] || [];
            for (var i = 0; i < arr.length; i++) { try { arr[i].call(facade, ev); } catch(e) {} }
          } catch(e) {}
        }
        function attachReal(r) {
          real = r;
          try {
            r.onmessage = function(ev) { emit('message', ev); };
            r.onerror = function(ev) { emit('error', ev); };
            r.onmessageerror = function(ev) { emit('messageerror', ev); };
          } catch(e) {}
          while (queue.length && !dead) {
            var m = queue.shift();
            try { real.postMessage(m[0], m[1]); } catch(e) {}
          }
        }
        facade.postMessage = maskFn(function(msg, transfer) {
          if (real && !dead) { try { real.postMessage(msg, transfer); } catch(e) {} }
          else if (!dead) queue.push([msg, transfer]);
        }, 'postMessage');
        facade.terminate = maskFn(function() { dead = true; queue = []; try { if (real) real.terminate(); } catch(e) {} }, 'terminate');
        facade.addEventListener = maskFn(function(t, l) { (listeners[t] = listeners[t] || []).push(l); }, 'addEventListener');
        facade.removeEventListener = maskFn(function(t, l) {
          var a = listeners[t] || [];
          var i = a.indexOf(l);
          if (i !== -1) a.splice(i, 1);
        }, 'removeEventListener');
        facade.dispatchEvent = maskFn(function() { return true; }, 'dispatchEvent');
        var fetchCode = function(done) {
          try {
            var xhr = new XMLHttpRequest();
            xhr.open('GET', s, true);
            xhr.onload = function() { done(xhr.status === 0 || (xhr.status >= 200 && xhr.status < 300) ? xhr.responseText : null); };
            xhr.onerror = function() { done(null); };
            xhr.send();
          } catch(e) { done(null); }
        };
        fetchCode(function(code) {
          if (dead) return;
          try {
            if (code === null) { attachReal(new OrigWorker(s, opts)); return; }
            var blob = new Blob([(window.__cpaWorkerShield || '') + '\n;' + code], { type: 'text/javascript' });
            attachReal(new OrigWorker(URL.createObjectURL(blob), opts));
          } catch(e) {
            try { attachReal(new OrigWorker(s, opts)); } catch(e2) {}
          }
        });
        return facade;
      };
      CpaWorker.__cpaBridged = true;
      CpaWorker.prototype = OrigWorker.prototype;
      window.Worker = maskFn(CpaWorker, 'Worker');
    }
  } catch(e) {}

  // ── واجهة الجسر العامة ──
  Object.defineProperty(window, '__cpaBridge', {
    value: {
      v: 1,
      note: note,
      stats: function() { var c = {}; for (var k in hits) c[k] = hits[k]; return { score: scoreNow(), hits: c }; }
    },
    enumerable: false, writable: false, configurable: true
  });
  return 'bridge_ready_v1';
})();
        """.trimIndent()
    }

    /**
     * Shield Self-Test — تدقيق ذاتي: يقرأ القيم المرصودة فعلياً (كأي موقع فاحص)
     * ويقارنها بالمتوقع، ويخزن النتيجة في window.__cpaSelfTestResult.
     * الأصلي يقرأها بعد ~2.5 ثانية (فحوص غير متزامنة: أصوات/تخزين/لوحة/بطارية).
     */
    fun buildShieldSelfTestScript(expectedLang: String = "en-US", expectedTz: String = "America/New_York"): String {
        val wantLang = expectedLang.trim().takeIf {
            it.matches(Regex("^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?$"))
        } ?: "en-US"
        val wantTz = expectedTz.trim().takeIf { it.contains("/") && !it.contains("{") } ?: "America/New_York"
        return """
(function() {
  window.__cpaSelfTestResult = null;
  var WANT_LANG = '$wantLang';
  var WANT_TZ = '$wantTz';
  var out = [];
  function chk(name, ok, got) { out.push({ n: name, ok: !!ok, got: String(got === undefined ? '' : got).slice(0, 72) }); }
  try {
    var ua = (navigator.userAgent || '').toLowerCase();
    var mob = ua.indexOf('android') !== -1 || ua.indexOf('iphone') !== -1 || ua.indexOf('mobile') !== -1;
    chk('webdriver_false', navigator.webdriver === false, navigator.webdriver);
    var plat = '';
    try { plat = navigator.platform || ''; } catch(e) {}
    var platOk = mob ? (plat.indexOf('Linux') === 0 || plat === 'iPhone') : (plat === 'Win32' || plat === 'MacIntel' || plat.indexOf('Linux') === 0);
    chk('platform_ua', platOk, plat);
    var cores = 0;
    try { cores = navigator.hardwareConcurrency || 0; } catch(e) {}
    chk('cores_plausible', [2, 4, 6, 8, 12, 16].indexOf(cores) !== -1, cores);
    var mem = -1;
    try { mem = (typeof navigator.deviceMemory === 'number') ? navigator.deviceMemory : -1; } catch(e) {}
    chk('memory_enum', mem === -1 || [0.25, 0.5, 1, 2, 4, 8].indexOf(mem) !== -1, mem);
    var langs = [];
    try { langs = navigator.languages || []; } catch(e) {}
    chk('language_match', langs.length > 0 && langs[0] === WANT_LANG, (langs[0] || '') + ' want ' + WANT_LANG);
    var rtz = '';
    try { rtz = Intl.DateTimeFormat().resolvedOptions().timeZone || ''; } catch(e) {}
    chk('intl_timezone', rtz === WANT_TZ, rtz);
    var off = 9999;
    try { off = new Date().getTimezoneOffset(); } catch(e) {}
    chk('tzoffset_sane', Math.abs(off) <= 840, off);
    var dpr = 0;
    try { dpr = window.devicePixelRatio || 0; } catch(e) {}
    chk('dpr_plausible', [1, 1.25, 1.5, 2, 2.25, 2.5, 2.625, 3].indexOf(dpr) !== -1, dpr);
    var sw = 0;
    try { sw = screen.width || 0; } catch(e) {}
    chk('screen_sane', sw >= 320 && sw <= 5120, sw + 'x' + (screen.height || 0));
    var pl = -1;
    try { pl = navigator.plugins ? navigator.plugins.length : -1; } catch(e) {}
    chk('plugins_sane', mob ? (pl === 0) : (pl >= 3), pl);
    var pdf = null;
    try { pdf = navigator.pdfViewerEnabled; } catch(e) {}
    chk('pdf_consistent', pdf === null || pdf === !mob, pdf);
    var cd = 0;
    try { cd = screen.colorDepth || 0; } catch(e) {}
    chk('colordepth_24', cd === 24, cd);
    var fine = false, coarse = false;
    try { fine = matchMedia('(pointer: fine)').matches; coarse = matchMedia('(pointer: coarse)').matches; } catch(e) {}
    chk('pointer_single', fine !== coarse, 'fine=' + fine + ' coarse=' + coarse);
    var light = false;
    try { light = matchMedia('(prefers-color-scheme: light)').matches; } catch(e) {}
    chk('scheme_light', light === true, light);
    var pngOk = false;
    try {
      var c = document.createElement('canvas');
      c.width = 8; c.height = 8;
      pngOk = (c.toDataURL().indexOf('data:image/png') === 0);
    } catch(e) {}
    chk('canvas_works', pngOk, pngOk);
    var glOk = false, glCount = -1;
    try {
      var g = document.createElement('canvas').getContext('webgl');
      if (g) {
        var exts = g.getSupportedExtensions() || [];
        glCount = exts.length;
        glOk = glCount > 0 && glCount <= 28;
      } else { glOk = true; glCount = 0; }
    } catch(e) {}
    chk('webgl_ext_capped', glOk, glCount);
    var r1 = '', r2 = '';
    try {
      var a = document.body.getClientRects();
      r1 = a.length > 0 ? (a[0].x + ',' + a[0].y) : 'empty';
      var b = document.body.getClientRects();
      r2 = b.length > 0 ? (b[0].x + ',' + b[0].y) : 'empty';
    } catch(e) {}
    chk('rects_stable', r1 === r2, r1);
    var np = '';
    try { np = window.Notification ? window.Notification.permission : 'absent'; } catch(e) {}
    chk('notification_sane', np === 'absent' || np === 'default' || np === 'granted' || np === 'denied', np);
    var ce = '';
    try { ce = navigator.connection ? navigator.connection.effectiveType : 'absent'; } catch(e) {}
    chk('connection_4g', ce === 'absent' || ce === '4g' || ce === '3g', ce);
    chk('chrome_obj', mob ? true : (typeof window.chrome !== 'undefined'), typeof window.chrome);
    chk('rtc_present', typeof window.RTCPeerConnection !== 'undefined', typeof window.RTCPeerConnection);
    var hasTouch = false;
    try { hasTouch = ('ontouchstart' in window); } catch(e) {}
    chk('touch_consistent', hasTouch === mob, 'touch=' + hasTouch + ' mobile=' + mob);
    var heapOk = true, heapGot = 'absent-api';
    try {
      if (window.performance && window.performance.memory && typeof navigator.deviceMemory === 'number') {
        var limGB = window.performance.memory.jsHeapSizeLimit / 1073741824;
        heapOk = limGB + 0.001 >= navigator.deviceMemory;
        heapGot = limGB.toFixed(1) + 'GB vs mem=' + navigator.deviceMemory;
      }
    } catch(e) { heapGot = 'throw'; }
    chk('heap_ge_memory', heapOk, heapGot);
    var gpuV = 'absent';
    try { gpuV = typeof navigator.gpu; } catch(e) {}
    chk('gpu_story', mob ? true : (gpuV !== 'undefined'), gpuV);
    var dwOk = false, dwGot = '';
    try {
      var sww = screen.width || 0;
      var q1 = matchMedia('(min-device-width: ' + sww + 'px)').matches;
      var q2 = matchMedia('(min-device-width: ' + (sww + 1) + 'px)').matches;
      dwOk = (q1 === true && q2 === false);
      dwGot = sww + 'px';
    } catch(e) { dwGot = 'throw'; }
    chk('css_device_dim', dwOk, dwGot);
    var pending = 5;
    function finishAsync() {
      pending--;
      if (pending <= 0) done();
    }
    function done() {
      var p = 0, f = [];
      for (var i = 0; i < out.length; i++) { if (out[i].ok) p++; else f.push(out[i].n + '=' + out[i].got); }
      window.__cpaSelfTestResult = { passed: p, failed: f.length, failures: f };
    }
    try {
      if (navigator.geolocation && navigator.geolocation.getCurrentPosition) {
        navigator.geolocation.getCurrentPosition(function(pos) {
          try {
            var la = pos.coords.latitude, lo = pos.coords.longitude;
            chk('geo_finite', isFinite(la) && isFinite(lo) && !(la === 0 && lo === 0), la + ',' + lo);
          } catch(e) { chk('geo_finite', false, 'cb-err'); }
          finishAsync();
        }, function() { chk('geo_finite', false, 'denied'); finishAsync(); }, { timeout: 1500 });
      } else { chk('geo_finite', false, 'absent'); finishAsync(); }
    } catch(e) { chk('geo_finite', false, 'throw'); finishAsync(); }
    try {
      if (window.speechSynthesis && speechSynthesis.getVoices) {
        var vs = [];
        try { vs = speechSynthesis.getVoices() || []; } catch(e) {}
        chk('voices_present', vs.length >= 1, vs.length);
      } else { chk('voices_present', true, 'absent-api'); }
    } catch(e) { chk('voices_present', false, 'throw'); }
    finishAsync();
    function storageDone(ok, got) {
      for (var i = 0; i < out.length; i++) { if (out[i].n === 'storage_quota') return; }
      chk('storage_quota', ok, got);
      finishAsync();
    }
    try {
      if (navigator.storage && navigator.storage.estimate) {
        navigator.storage.estimate().then(function(es) {
          storageDone(es && es.quota > 1000000000, es ? es.quota : 0);
        }).catch(function() { storageDone(false, 'reject'); });
        setTimeout(function() { if (pending > 0) storageDone(false, 'timeout'); }, 1800);
      } else { chk('storage_quota', true, 'absent-api'); finishAsync(); }
    } catch(e) { chk('storage_quota', false, 'throw'); finishAsync(); }
    try {
      if (navigator.keyboard && navigator.keyboard.getLayoutMap) {
        navigator.keyboard.getLayoutMap().then(function(m) {
          var sz = 0;
          try { sz = m.size || 0; } catch(e) {}
          chk('kbd_map', sz >= 20, sz);
          finishAsync();
        }).catch(function() { chk('kbd_map', false, 'reject'); finishAsync(); });
      } else { chk('kbd_map', true, 'absent-api'); finishAsync(); }
    } catch(e) { chk('kbd_map', false, 'throw'); finishAsync(); }
    try {
      if (navigator.getBattery) {
        navigator.getBattery().then(function(b) {
          var lv = -1;
          try { lv = b.level; } catch(e) {}
          chk('battery_sane', lv > 0 && lv <= 1, lv);
          finishAsync();
        }).catch(function() { chk('battery_sane', false, 'reject'); finishAsync(); });
      } else { chk('battery_sane', true, 'absent-api'); finishAsync(); }
    } catch(e) { chk('battery_sane', false, 'throw'); finishAsync(); }
  } catch(e) {
    chk('harness', false, String(e && e.message || e).slice(0, 60));
  }
})();
        """.trimIndent()
    }

    fun buildTimezoneScript(timezone: String, language: String): String {
        val cleanTz = timezone.replace("'", "")
        val cleanLang = language.replace("'", "")
        return """
(function() {
  try {
    var rawTz = '$cleanTz';
    var rawLang = '$cleanLang';
    var tz = (rawTz && rawTz.indexOf('/') !== -1 && rawTz.indexOf('{') === -1) ? rawTz.trim() : 'America/New_York';
    var lang = (rawLang && /^[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})?$/.test(rawLang.trim())) ? rawLang.trim() : 'en-US';
    Object.defineProperty(window, '__cpaTargetTz', { value: tz, enumerable: false, writable: true, configurable: true });
    Object.defineProperty(window, '__cpaTargetLang', { value: lang, enumerable: false, writable: true, configurable: true });

    var maskFn = function(fn, name) {
      try {
        if (window.__cpaToStringMap && typeof fn === 'function') {
          Object.defineProperty(fn, 'name', { value: name || '', configurable: true });
          window.__cpaToStringMap.set(fn, 'function ' + (name || '') + '() { [native code] }');
        }
      } catch(e) {}
      return fn;
    };

    var navP = (typeof Navigator !== 'undefined' && Navigator.prototype) ? Navigator.prototype : navigator;
    try {
      Object.defineProperty(navP, 'language', { get: maskFn(function() { return window.__cpaTargetLang || lang; }, 'get language'), enumerable: true, configurable: true });
      Object.defineProperty(navP, 'languages', { get: maskFn(function() { var l = window.__cpaTargetLang || lang; return [l, l.split('-')[0]]; }, 'get languages'), enumerable: true, configurable: true });
    } catch(e) {}

    function calculateTzOffset(dateObj, ianaTz) {
      try {
        var d = (dateObj instanceof Date && !isNaN(dateObj.getTime())) ? dateObj : new Date();
        var utcStr = d.toLocaleString('en-US', { timeZone: 'UTC', hour12: false });
        var tzStr = d.toLocaleString('en-US', { timeZone: ianaTz, hour12: false });
        return Math.round((Date.parse(utcStr) - Date.parse(tzStr)) / 60000);
      } catch(err) {
        return 300;
      }
    }
    Date.prototype.getTimezoneOffset = maskFn(function() {
      return calculateTzOffset(this, window.__cpaTargetTz || tz);
    }, 'getTimezoneOffset');

    // Intl.DateTimeFormat().resolvedOptions().timeZone — must agree with the spoofed zone
    try {
      if (typeof Intl !== 'undefined' && Intl.DateTimeFormat) {
        var __cpaODTF = Intl.DateTimeFormat;
        var __cpaOrigRes = __cpaODTF.prototype.resolvedOptions;
        __cpaODTF.prototype.resolvedOptions = maskFn(function() {
          var rr = __cpaOrigRes.call(this);
          try { rr.timeZone = window.__cpaTargetTz || tz; } catch(e) {}
          try {
            var ll = window.__cpaTargetLang || lang;
            rr.locale = ll;
          } catch(e2) {}
          return rr;
        }, 'resolvedOptions');
      }
    } catch(e) {}
  } catch(e) {}
})();
true;
        """.trimIndent()
    }

    /**
     * Builds a standalone JavaScript injection script loaded on page start that modifies the
     * Canvas API (CanvasRenderingContext2D.getImageData, HTMLCanvasElement.toDataURL,
     * HTMLCanvasElement.toBlob, OffscreenCanvas, and WebGL readPixels) to return deterministic
     * per-session noise, defeating both Canvas/WebGL fingerprinting and multi-read consistency checks.
     */
    fun buildCanvasNoiseScript(): String {
        return """
(function() {
  if (window.__cpaCanvasNoiseInjected) return;
  Object.defineProperty(window, '__cpaCanvasNoiseInjected', { value: true, enumerable: false, configurable: true });

  var seed = (window.__cpaSessionSeed || 2166136261) >>> 0;
  function hashIdx(n) {
    var x = ((n ^ seed) + 0x9e3779b9) >>> 0;
    x = Math.imul(x ^ (x >>> 16), 0x85ebca6b) >>> 0;
    x = Math.imul(x ^ (x >>> 13), 0xc2b2ae35) >>> 0;
    return (x ^ (x >>> 16)) >>> 0;
  }
  function maskFn(fn, name) {
    try {
      if (window.__cpaToStringMap && typeof fn === 'function') {
        Object.defineProperty(fn, 'name', { value: name || '', configurable: true });
        window.__cpaToStringMap.set(fn, 'function ' + (name || '') + '() { [native code] }');
      }
    } catch(e) {}
    return fn;
  }

  function addDeterministicNoiseToImageData(imageData) {
    try {
      if (!imageData || !imageData.data) return;
      var data = imageData.data;
      var len = data.length;
      if (len < 64) return;
      var stride = len > 40000 ? 64 : 16;
      for (var i = 0; i < len - 4; i += stride) {
        if (data[i + 3] > 16) {
          var h = hashIdx(i);
          var ch = h % 3;
          var delta = (h & 4) ? 1 : -1;
          var val = data[i + ch];
          data[i + ch] = ((val + seed) & 1) ? Math.min(255, Math.max(0, val + delta)) : val;
        }
      }
    } catch(e) {}
  }

  // 1. Hook CanvasRenderingContext2D.prototype.getImageData & HTMLCanvasElement export
  try {
    if (window.CanvasRenderingContext2D && !CanvasRenderingContext2D.prototype.__cpaCanvasPatched) {
      Object.defineProperty(CanvasRenderingContext2D.prototype, '__cpaCanvasPatched', { value: true, enumerable: false, configurable: true });
      var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
      var origPutImageData = CanvasRenderingContext2D.prototype.putImageData;

      CanvasRenderingContext2D.prototype.getImageData = maskFn(function(sx, sy, sw, sh) {
        var imgData = origGetImageData.apply(this, arguments);
        addDeterministicNoiseToImageData(imgData);
        return imgData;
      }, 'getImageData');

      if (window.HTMLCanvasElement) {
        var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
        var origToBlob = HTMLCanvasElement.prototype.toBlob;

        function cloneCanvasWithNoise(srcCanvas) {
          try {
            if (!srcCanvas || srcCanvas.width === 0 || srcCanvas.height === 0 || srcCanvas.width > 2048 || srcCanvas.height > 2048) return srcCanvas;
            var clone = document.createElement('canvas');
            clone.width = srcCanvas.width;
            clone.height = srcCanvas.height;
            var cctx = clone.getContext('2d');
            if (!cctx) return srcCanvas;
            cctx.drawImage(srcCanvas, 0, 0);
            var sampleW = Math.min(clone.width, 64);
            var sampleH = Math.min(clone.height, 64);
            var slice = origGetImageData.call(cctx, 0, 0, sampleW, sampleH);
            addDeterministicNoiseToImageData(slice);
            origPutImageData.call(cctx, slice, 0, 0);
            return clone;
          } catch(e) {
            return srcCanvas;
          }
        }

        HTMLCanvasElement.prototype.toDataURL = maskFn(function(type, encoderOptions) {
          var target = cloneCanvasWithNoise(this);
          return origToDataURL.apply(target, arguments);
        }, 'toDataURL');

        HTMLCanvasElement.prototype.toBlob = maskFn(function(callback, type, quality) {
          var target = cloneCanvasWithNoise(this);
          return origToBlob.apply(target, arguments);
        }, 'toBlob');
      }
    }
  } catch(e) {}

  // 2. Hook OffscreenCanvas if available
  try {
    if (window.OffscreenCanvas && !OffscreenCanvas.prototype.__cpaOffscreenPatched) {
      Object.defineProperty(OffscreenCanvas.prototype, '__cpaOffscreenPatched', { value: true, enumerable: false, configurable: true });
      if (window.OffscreenCanvasRenderingContext2D) {
        var origOffscreenGetImageData = OffscreenCanvasRenderingContext2D.prototype.getImageData;
        OffscreenCanvasRenderingContext2D.prototype.getImageData = maskFn(function(sx, sy, sw, sh) {
          var imgData = origOffscreenGetImageData.apply(this, arguments);
          addDeterministicNoiseToImageData(imgData);
          return imgData;
        }, 'getImageData');
      }
    }
  } catch(e) {}
})();
true;
        """.trimIndent()
    }

    fun buildSmartFormFillScript(
        identity: GeneratedIdentity,
        categories: String = "",
        clickTexts: List<String> = emptyList(),
        activeClickText: String? = null,
        extractedInfo: ExtractedInfo? = null,
        brainMode: String = "normal",
        personaFactsJson: String = "",
        humanSimulationEnabled: Boolean = true,
        humanSimulationProfile: String = "natural",
        stageTransitionWaitMinSec: Int = 3,
        stageTransitionWaitMaxSec: Int = 7,
        humanTypingSimulation: Boolean = true,
        humanPointerAndTouchPaths: Boolean = true,
        humanReadingPauseBeforeAction: Boolean = true
    ): String {
        val planJson = TaskCategoryPlanner.buildPlanJson(TaskCategoryPlanner.parseCategories(categories))
        val clickTextsArray = JSONArray().apply {
            clickTexts.filter { it.isNotBlank() }.forEach { put(it) }
        }.toString()
        val activeTargetJson = if (activeClickText.isNullOrBlank()) "null" else JSONObject.quote(activeClickText)
        val rawCategoriesJson = JSONObject.quote(categories)
        val safeBrainMode = JSONObject.quote(brainMode.ifBlank { "normal" })
        val safeHumanProfile = JSONObject.quote(humanSimulationProfile.ifBlank { "natural" })
        val resolvedPersonaFacts = personaFactsJson.takeIf { it.isNotBlank() && it != "{}" }
            ?: IdentityService.buildPersonaFactsJson(identity)

        val identityJson = JSONObject().apply {
            put("firstName", identity.firstName)
            put("lastName", identity.lastName)
            put("fullName", identity.fullName)
            put("email", identity.email)
            put("phone", identity.phone)
            put("address", identity.address)
            put("city", identity.city.ifBlank { extractedInfo?.city ?: "" })
            put("state", identity.state.ifBlank { extractedInfo?.region ?: "" })
            put("postalCode", identity.postalCode.ifBlank { extractedInfo?.postalCode ?: "10001" })
            put("country", identity.country.ifBlank { extractedInfo?.country ?: "United States" })
            put("birthDate", identity.birthDate)
            put("gender", identity.gender)
            put("username", identity.username)
            put("password", identity.password)
            put("cardNumber", identity.cardNumber)
            put("cardExpiry", identity.cardExpiry)
            put("cardCvv", identity.cardCvv)
            put("cardHolder", identity.cardHolder)
            put("cardType", identity.cardType)
            put("bankName", identity.bankName)
            put("ip", extractedInfo?.ip ?: "")
            put("timezone", extractedInfo?.timezone ?: "")
            put("language", extractedInfo?.language ?: "")
            put("isp", extractedInfo?.isp ?: "")
        }.toString()

        return """
(function() {
  window._cpaIdentity = $identityJson;
  window._cpaPersonaFacts = $resolvedPersonaFacts;
  window._cpaBrainMode = $safeBrainMode;
  window._cpaCategoryPlan = $planJson;
  window._cpaClickTexts = $clickTextsArray;
  window._cpaActiveClickText = $activeTargetJson;
  window._cpaCategoriesRaw = $rawCategoriesJson;
  window._cpaAnsweredQuestions = window._cpaAnsweredQuestions || {};
  window._cpaHumanConfig = {
    enabled: $humanSimulationEnabled,
    profile: $safeHumanProfile,
    stageWaitMinSec: $stageTransitionWaitMinSec,
    stageWaitMaxSec: $stageTransitionWaitMaxSec,
    typingSim: $humanTypingSimulation,
    pointerPaths: $humanPointerAndTouchPaths,
    readingPause: $humanReadingPauseBeforeAction
  };
  window._cpaPointerX = window._cpaPointerX || Math.floor((window.innerWidth || 360) * 0.45);
  window._cpaPointerY = window._cpaPointerY || Math.floor((window.innerHeight || 640) * 0.35);

  function logCpa(msg) {
    // Silent internal status hook — never write [CPA] strings to window.console
  }

  function reportHumanAction(statusTextAr) {
    try {
      if (window.AndroidBridge && typeof window.AndroidBridge.onHumanActionStatus === 'function') {
        window.AndroidBridge.onHumanActionStatus(statusTextAr);
      }
    } catch(e) {}
  }

  function getProfileSpeedFactor() {
    var prof = (window._cpaHumanConfig && window._cpaHumanConfig.profile) || 'natural';
    if (prof === 'careful') return 1.45;
    if (prof === 'fast_human') return 0.68;
    return 1.0;
  }

  function computeHumanReadingDelayMs(textLength) {
    if (!window._cpaHumanConfig || !window._cpaHumanConfig.enabled || !window._cpaHumanConfig.readingPause) return 180;
    var factor = getProfileSpeedFactor();
    var baseMs = 420 + Math.min(1800, Math.floor((textLength || 45) * 8.5));
    var jitter = Math.floor((Math.random() - 0.35) * 320);
    return Math.max(260, Math.floor((baseMs + jitter) * factor));
  }

  function showFloatingBadge(text) {
    // Pure browsing mode: No DOM pollution or injected elements on the webpage
  }

  function setNativeValue(element, value) {
    if (!element || value === undefined || value === null) return;
    try {
      var strVal = String(value);
      var lastValue = element.value;
      var prototype = element.tagName === 'INPUT' ? window.HTMLInputElement.prototype :
                      element.tagName === 'SELECT' ? window.HTMLSelectElement.prototype :
                      window.HTMLTextAreaElement.prototype;
      var setter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set;

      // Simulate realistic human focus & keystroke sequence when human typing simulation is active
      if (window._cpaHumanConfig && window._cpaHumanConfig.enabled && window._cpaHumanConfig.typingSim && element.tagName !== 'SELECT') {
        try {
          if (typeof element.focus === 'function') element.focus();
          element.dispatchEvent(new FocusEvent('focus', { bubbles: false }));
          element.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
          // Simulate progressive keystroke events on a sample of characters so anti-bot key-listeners record authentic cadence
          var sampleChars = strVal.slice(0, Math.min(strVal.length, 14));
          var partial = '';
          for (var c = 0; c < sampleChars.length; c++) {
            var ch = sampleChars[c];
            var keyCode = ch.charCodeAt(0);
            partial += ch;
            element.dispatchEvent(new KeyboardEvent('keydown', { key: ch, code: 'Key' + ch.toUpperCase(), keyCode: keyCode, which: keyCode, bubbles: true }));
            element.dispatchEvent(new KeyboardEvent('keypress', { key: ch, charCode: keyCode, keyCode: keyCode, which: keyCode, bubbles: true }));
            if (setter) setter.call(element, partial); else element.value = partial;
            try {
              element.dispatchEvent(new InputEvent('input', { data: ch, inputType: 'insertText', bubbles: true }));
            } catch(ie) {
              element.dispatchEvent(new Event('input', { bubbles: true }));
            }
            element.dispatchEvent(new KeyboardEvent('keyup', { key: ch, code: 'Key' + ch.toUpperCase(), keyCode: keyCode, which: keyCode, bubbles: true }));
          }
        } catch(kErr) {}
      }

      if (setter) {
        setter.call(element, strVal);
      } else {
        element.value = strVal;
      }
      // React 16+ input tracker update
      var tracker = element._valueTracker;
      if (tracker) {
        tracker.setValue(lastValue);
      }
      element.dispatchEvent(new Event('input', { bubbles: true }));
      element.dispatchEvent(new Event('change', { bubbles: true }));
      element.dispatchEvent(new Event('blur', { bubbles: true }));
      element.dispatchEvent(new FocusEvent('focusout', { bubbles: true }));
    } catch(err) {
      element.value = value;
      element.dispatchEvent(new Event('input', { bubbles: true }));
      element.dispatchEvent(new Event('change', { bubbles: true }));
    }
  }

  function isElementInViewport(el) {
    if (!el) return true;
    try {
      var rect = el.getBoundingClientRect();
      return (
        rect.top >= 0 &&
        rect.left >= 0 &&
        rect.bottom <= (window.innerHeight || document.documentElement.clientHeight) &&
        rect.right <= (window.innerWidth || document.documentElement.clientWidth)
      );
    } catch(e) { return true; }
  }

  function triggerClick(el) {
    if (!el) return;
    try {
      if (!isElementInViewport(el)) {
        el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      }
      if (el.disabled) el.disabled = false;
      if (el.removeAttribute) el.removeAttribute('disabled');
    } catch(e) {}

    try {
      var rect = el.getBoundingClientRect();
      var w = Math.max(12, rect.width || 36);
      var h = Math.max(12, rect.height || 24);
      // Fitts's Law Gaussian target jitter around element center (avoids robotic exact 50% center clicks)
      var jitterX = (Math.random() + Math.random() - 1.0) * Math.min(w * 0.28, 18);
      var jitterY = (Math.random() + Math.random() - 1.0) * Math.min(h * 0.28, 10);
      var cx = Math.floor(rect.left + (w / 2) + jitterX);
      var cy = Math.floor(rect.top + (h / 2) + jitterY);

      // Simulate Bezier curve pointer approach from last pointer position
      if (window._cpaHumanConfig && window._cpaHumanConfig.enabled && window._cpaHumanConfig.pointerPaths) {
        var startX = window._cpaPointerX || Math.max(20, cx - 90);
        var startY = window._cpaPointerY || Math.max(20, cy - 70);
        var ctrlX = (startX + cx) / 2 + (Math.random() - 0.5) * 60;
        var ctrlY = (startY + cy) / 2 + (Math.random() - 0.5) * 45;
        var steps = 5;
        for (var s = 1; s <= steps; s++) {
          var t = s / steps;
          var inv = 1 - t;
          var bx = Math.floor(inv * inv * startX + 2 * inv * t * ctrlX + t * t * cx);
          var by = Math.floor(inv * inv * startY + 2 * inv * t * ctrlY + t * t * cy);
          var moveOpts = { bubbles: true, cancelable: true, view: window, clientX: bx, clientY: by };
          document.dispatchEvent(new PointerEvent('pointermove', moveOpts));
          document.dispatchEvent(new MouseEvent('mousemove', moveOpts));
        }
        window._cpaPointerX = cx;
        window._cpaPointerY = cy;
      }

      var opts = { bubbles: true, cancelable: true, view: window, clientX: cx, clientY: cy, button: 0, buttons: 1 };
      el.dispatchEvent(new PointerEvent('pointerover', opts));
      el.dispatchEvent(new MouseEvent('mouseover', opts));
      el.dispatchEvent(new PointerEvent('pointerenter', opts));
      el.dispatchEvent(new MouseEvent('mouseenter', opts));
      el.dispatchEvent(new PointerEvent('pointerdown', opts));
      el.dispatchEvent(new MouseEvent('mousedown', opts));
      if (typeof el.focus === 'function') el.focus();
      el.dispatchEvent(new PointerEvent('pointerup', opts));
      el.dispatchEvent(new MouseEvent('mouseup', opts));
      el.dispatchEvent(new MouseEvent('click', opts));
    } catch(e) {}
    if (typeof el.click === 'function') {
      try { el.click(); } catch(e2) {}
    }
  }

  function getAssociatedLabelText(el) {
    if (!el) return '';
    var parts = [];
    try {
      if (el.labels && el.labels.length > 0) {
        for (var i = 0; i < el.labels.length; i++) {
          parts.push(el.labels[i].innerText || el.labels[i].textContent || '');
        }
      }
      if (el.id) {
        var lbl = document.querySelector('label[for="' + el.id.replace(/"/g, '\\"') + '"]');
        if (lbl) parts.push(lbl.innerText || lbl.textContent || '');
      }
      var parentLbl = el.closest ? el.closest('label, .form-group, .field, .input-group') : null;
      if (parentLbl) {
        parts.push(parentLbl.innerText || parentLbl.textContent || '');
      } else if (el.previousElementSibling) {
        parts.push(el.previousElementSibling.innerText || el.previousElementSibling.textContent || '');
      }
    } catch(e) {}
    return parts.join(' ').toLowerCase();
  }

  function matchField(el) {
    var ac = (el.getAttribute && (el.getAttribute('autocomplete') || '')) ? el.getAttribute('autocomplete').toLowerCase() : '';
    var im = (el.getAttribute && (el.getAttribute('inputmode') || '')) ? el.getAttribute('inputmode').toLowerCase() : '';
    var raw = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.placeholder || '') + ' ' + (el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('data-field') || '') + ' ' + (el.className || '') + ' ' + ac + ' ' + getAssociatedLabelText(el)).toLowerCase();
    var type = (el.type || '').toLowerCase();

    if (ac === 'email' || type === 'email' || im === 'email' || raw.match(/email|e-mail|correo|courriel|بريد|ايميل|إيميل/)) return 'email';
    if (raw.match(/areacode|area.?code|phone.?1|tel.?1|ph.?1/)) return 'phoneArea';
    if (raw.match(/prefix|phone.?2|tel.?2|ph.?2/)) return 'phonePrefix';
    if (raw.match(/line.?num|phone.?3|tel.?3|ph.?3/)) return 'phoneLine';
    if (ac === 'tel' || type === 'tel' || im === 'tel' || raw.match(/phone|tel|mobile|cel|movil|telephone|contact|هاتف|جوال|موبايل|رقم/)) return 'phone';
    if (raw.match(/dob.?month|birth.?month|month.?of.?birth|bday-month/)) return 'dobMonth';
    if (raw.match(/dob.?day|birth.?day|day.?of.?birth|bday-day/)) return 'dobDay';
    if (raw.match(/dob.?year|birth.?year|year.?of.?birth|bday-year/)) return 'dobYear';
    if (ac === 'given-name' || raw.match(/first.?name|fname|given.?name|prenom|vorname|الاسم الأول|الاسم الاول/)) return 'firstName';
    if (ac === 'family-name' || raw.match(/last.?name|lname|surname|family.?name|nom|nachname|اسم العائلة|اللقب/)) return 'lastName';
    if (ac === 'name' || raw.match(/full.?name|your.?name|nombre.?completo|nom.?complet|الاسم الكامل|الاسم/)) return 'fullName';
    if (ac === 'postal-code' || raw.match(/zip|postal|postcode|plz|cap|pincode|رمز بريدي/)) return 'postalCode';
    if (ac === 'street-address' || ac === 'address-line1' || raw.match(/address|addr|street|rue|strasse|calle|via|direccion|عنوان/)) return 'address';
    if (ac === 'address-level2' || raw.match(/city|ville|stadt|ciudad|citta|town|مدينة/)) return 'city';
    if (ac === 'address-level1' || raw.match(/state|province|region|estado|departamento|ولاية|محافظة/)) return 'state';
    if (ac === 'country' || ac === 'country-name' || raw.match(/country|pays|land|pais|دولة|بلد/)) return 'country';
    if (ac === 'bday' || type === 'date' || raw.match(/birth|dob|birthday|date.?of.?birth|ميلاد/)) return 'birthDate';
    if (ac === 'sex' || raw.match(/gender|sex|sexe|جنس/)) return 'gender';
    if (raw.match(/card.?number|cardnum|cc.?num|numero.?carte/)) return 'cardNumber';
    if (raw.match(/expiry|expiration|exp.?date|mm.?yy|valid/)) return 'cardExpiry';
    if (raw.match(/cvv|cvc|cvn|security.?code/)) return 'cardCvv';
    if (type === 'password' || raw.match(/password|passcode|pwd|secret|passwort|mot.?de.?passe|contrasena|كلمة المرور|كلمة السر/)) return 'password';
    if (ac === 'username' || raw.match(/username|user.?name|login|usuario|utilisateur|pseudo|benutzer|اسم المستخدم/)) return 'username';
    if (raw.match(/card.?holder|cardholder|name.?on.?card|nom.?sur.?la.?carte|titular/)) return 'cardHolder';
    if (raw.match(/exp.?month|card.?month|month.?of.?expir/)) return 'cardExpMonth';
    if (raw.match(/exp.?year|card.?year|year.?of.?expir/)) return 'cardExpYear';
    if (raw.match(/bank.?name|bank|issuing.?bank/)) return 'bankName';
    if (raw.match(/\bage\b|years?.?old|عمر|السن/)) return 'age';
    return null;
  }

  // --- Intelligent Survey Understanding Engine ---

  function getQuestionContext(element) {
    if (!element) return '';
    var curr = element.parentElement;
    var depth = 0;
    while (curr && depth < 6) {
      // Look for question headers inside container
      var headers = curr.querySelectorAll('h1, h2, h3, h4, h5, legend, [class*="question"], [class*="prompt"], [class*="title"], [class*="survey-header"], [id*="question"]');
      for (var h = 0; h < headers.length; h++) {
        var hEl = headers[h];
        if (hEl !== element && !element.contains(hEl)) {
          var txt = (hEl.innerText || hEl.textContent || '').trim().toLowerCase();
          if (txt.length > 3) return txt;
        }
      }
      // Look for preceding sibling with text
      var prev = curr.previousElementSibling;
      if (prev) {
        var ptxt = (prev.innerText || prev.textContent || '').trim().toLowerCase();
        if (ptxt.includes('?') || ptxt.includes('how') || ptxt.includes('what') || ptxt.includes('are you') || ptxt.includes('do you') || ptxt.includes('select') || ptxt.includes('choose')) {
          return ptxt;
        }
      }
      curr = curr.parentElement;
      depth++;
    }
    return '';
  }

  function pickBestSurveyOption(questionText, optionsList, ident) {
    if (!optionsList || optionsList.length === 0) return null;
    var q = (questionText || '').toLowerCase();

    function matches(opt, regex) {
      var str = ((opt.text || '') + ' ' + (opt.value || '')).toLowerCase();
      return regex.test(str);
    }

    // 1. Age & Majority Verification (CRITICAL for qualification)
    if (q.match(/age|18|years.?old|how.?old|birth|dob/)) {
      var adultOpt = optionsList.find(function(o) { return matches(o, /\b(yes|18\+|18-24|25-34|35-44|over 18|older)\b/); });
      if (adultOpt) return adultOpt;
      var nonMinor = optionsList.find(function(o) { return !matches(o, /\b(no|under 18|<18|17|minor)\b/); });
      if (nonMinor) return nonMinor;
    }

    // 2. US / Residency / Location Qualification
    if (q.match(/us.?resident|united.?states|citizen|live in the us|country|residence/)) {
      var usOpt = optionsList.find(function(o) { return matches(o, /\b(yes|united states|usa|us)\b/); });
      if (usOpt) return usOpt;
      var nonNo = optionsList.find(function(o) { return !matches(o, /\bno\b/); });
      if (nonNo) return nonNo;
    }

    // 3. Gender / Sex Matching
    if (q.match(/gender|sex|are you male|man or woman/)) {
      var isFemale = ident && ((ident.gender && ident.gender.toLowerCase().includes('female')) || (ident.firstName && /^(mary|patricia|jennifer|linda|elizabeth|barbara|susan|jessica|sarah|karen|nancy|lisa|betty|margaret|sandra|ashley|kimberly|emily|donna|michelle|carol|amanda|melissa|deborah|stephanie|rebecca|sharon|laura|cynthia|kathleen|amy|shirley|angela|helen|anna|brenda|pamela|nicole|emma|samantha|katherine|christine|debra|rachel|catherine|carolyn|janet|ruth|maria|heather|diane|virginia|julie|joyce|victoria|olivia|kelly|christina|lauren|joan|evelyn|judith|megan|cheryl|andrea|hannah|martha|jacqueline|frances|gloria|ann|teresa|kathryn|sara|janice|jean|alice|madison|doris|abigail|julia|judy|grace|denise|amber|marilyn|beverly|danielle|theresa|sophia|marie|diana|brittany|natalie|isabella|charlotte|rose|kayla|alexis)/i.test(ident.firstName)));
      if (isFemale) {
        var femOpt = optionsList.find(function(o) { return matches(o, /\b(female|woman|f|femme)\b/); });
        if (femOpt) return femOpt;
      } else {
        var maleOpt = optionsList.find(function(o) { return matches(o, /\b(male|man|m|homme)\b/); });
        if (maleOpt) return maleOpt;
      }
      return optionsList[0];
    }

    // 4. Shopping, Online Habits, Smartphone, Device
    if (q.match(/shop|online|internet|smartphone|mobile|device|phone|buy|store|amazon|walmart/)) {
      var frequentOpt = optionsList.find(function(o) { return matches(o, /\b(yes|daily|weekly|often|frequently|regularly|always|iphone|android|yes, i do)\b/); });
      if (frequentOpt) return frequentOpt;
    }

    // 5. Employment & Occupation
    if (q.match(/employ|job|work|occupation|career/)) {
      var empOpt = optionsList.find(function(o) { return matches(o, /\b(employed|full.?time|yes|professional)\b/); });
      if (empOpt) return empOpt;
    }

    // 6. Household Income (pick middle/upper-middle tier)
    if (q.match(/income|earn|salary|household/)) {
      var midIncome = optionsList.find(function(o) { return matches(o, /(50|60|75|80|100)k?|\$50|\$75/); });
      if (midIncome) return midIncome;
      if (optionsList.length >= 3) return optionsList[Math.floor(optionsList.length / 2)];
    }

    // 7. Homeownership
    if (q.match(/own or rent|homeowner|housing|property|residence type/)) {
      var ownOpt = optionsList.find(function(o) { return matches(o, /\b(own|homeowner|house|single family)\b/); });
      if (ownOpt) return ownOpt;
    }

    // 7b. Credit Score / Financial Tier (Persona Coherence)
    if (q.match(/credit.?score|credit.?rating|fico|credit.?history/)) {
      var goodCredit = optionsList.find(function(o) { return matches(o, /\b(good|excellent|700|720|740|680|670|fair)\b/); });
      if (goodCredit) return goodCredit;
    }

    // 7c. Education / Marital / Family / Vehicle / Insurance
    if (q.match(/education|degree|school|college/)) {
      var eduOpt = optionsList.find(function(o) { return matches(o, /\b(bachelor|college|degree|graduate|associate|some college)\b/); });
      if (eduOpt) return eduOpt;
    }
    if (q.match(/marital|married|relationship/)) {
      var marOpt = optionsList.find(function(o) { return matches(o, /\b(married|single)\b/); });
      if (marOpt) return marOpt;
    }
    if (q.match(/insurance|insured|auto|car|vehicle/)) {
      var insOpt = optionsList.find(function(o) { return matches(o, /\b(yes|currently insured|own|1|2|geico|state farm|progressive|allstate)\b/); });
      if (insOpt) return insOpt;
    }

    // Brain Escalation Mode: if stuck on a survey question, try alternative non-disqualifying option #2 or #3
    var bMode = window._cpaBrainMode || 'normal';
    var validChoices = optionsList.filter(function(o) { return !matches(o, /\b(select|choose|pick|--|under 18|<18)\b/); });
    if (bMode === 'alt_option_2' && validChoices.length >= 2) {
      return validChoices[1];
    }
    if ((bMode === 'alt_option_3' || bMode === 'force_submit') && validChoices.length >= 2) {
      return validChoices[Math.min(2, validChoices.length - 1)];
    }

    // 8. Co-Reg Sponsor Deals / Upsells / Paid Offers / Insurance / Credit Cards
    // In co-reg walls, to proceed without payment, click "No thanks", "Skip", "Not interested"
    if (q.match(/special offer|sponsor|deal|partner|free trial|sign up for|subscription|quote|insurance|solar|card offer/)) {
      var passOpt = optionsList.find(function(o) { return matches(o, /\b(no thanks|no, thanks|no|skip|pass|not interested|continue without|no thank you|not at this time)\b/); });
      if (passOpt) return passOpt;
    }

    // 9. Ratings / Scales (e.g. 1 to 5 or 1 to 10)
    var numOptions = optionsList.filter(function(o) { return /^\d+$/.test((o.text || '').trim()); });
    if (numOptions.length >= 4) {
      return numOptions[numOptions.length - 2] || numOptions[numOptions.length - 1];
    }

    // 10. Binary Yes / No (Qualification questions almost always require "Yes")
    var yesOpt = optionsList.find(function(o) { return matches(o, /^\s*(yes|oui|si|agree|correct|definitely|absolutely)\b/); });
    var noOpt = optionsList.find(function(o) { return matches(o, /^\s*(no|non|disagree)\b/); });
    if (yesOpt && noOpt) {
      return yesOpt;
    }

    // 11. Positive sentiment matching
    var positiveOpt = optionsList.find(function(o) { return matches(o, /\b(yes|interested|agree|claim|participate|confirm|enter|proceed)\b/); });
    if (positiveOpt) return positiveOpt;

    // 12. Skip placeholders like "Select...", "Choose one..."
    var nonPlaceholder = optionsList.find(function(o) { return !matches(o, /\b(select|choose|pick|--|none)\b/); });
    if (nonPlaceholder) return nonPlaceholder;

    return optionsList[0];
  }

  // --- Handlers for Different Survey Types ---

  function isElementOrLabelVisible(el) {
    if (!el) return false;
    try {
      if (el.offsetParent !== null || el.offsetWidth > 0 || el.offsetHeight > 0) return true;
      if (el.labels && el.labels.length > 0) {
        for (var i = 0; i < el.labels.length; i++) {
          if (el.labels[i].offsetParent !== null || el.labels[i].offsetWidth > 0) return true;
        }
      }
      if (el.id) {
        var lbl = document.querySelector('label[for="' + el.id.replace(/"/g, '\\"') + '"]');
        if (lbl && (lbl.offsetParent !== null || lbl.offsetWidth > 0)) return true;
      }
      var parentLbl = el.closest ? el.closest('label') : null;
      if (parentLbl && (parentLbl.offsetParent !== null || parentLbl.offsetWidth > 0)) return true;
      if (el.parentElement && (el.parentElement.offsetParent !== null || el.parentElement.offsetWidth > 0)) return true;
    } catch(e) {}
    return false;
  }

  function findVisibleLabelForInput(el) {
    if (!el) return null;
    try {
      if (el.labels && el.labels.length > 0 && el.labels[0].offsetParent !== null) return el.labels[0];
      if (el.id) {
        var lbl = document.querySelector('label[for="' + el.id.replace(/"/g, '\\"') + '"]');
        if (lbl && lbl.offsetParent !== null) return lbl;
      }
      var parentLbl = el.closest ? el.closest('label') : null;
      if (parentLbl && parentLbl.offsetParent !== null) return parentLbl;
    } catch(e) {}
    return null;
  }

  function handleButtonSurveys(ident) {
    // Find all choice buttons / cards inside survey containers or option lists (including pre-landers where buttons use .btn or .continue)
    var choiceSelectors = [
      'button',
      '[role="button"]',
      '.survey-btn', '.quiz-option', '.answer', '.choice', '.option-card', '.btn-option',
      '[data-answer]', '[data-choice]', '[data-value]', '[data-option]', '.poll-option', '.survey-tile',
      '[class*="answer"]', '[class*="option"]', '[class*="choice"]', '[class*="quiz-btn"]',
      'a.btn', 'a.button', 'div.btn', 'div.button', 'label.btn', 'label.radio', 'li[onclick]', 'div[onclick]', 'a[onclick]'
    ];

    var allButtons = Array.from(document.querySelectorAll(choiceSelectors.join(',')));
    if (allButtons.length === 0) return false;

    // Group buttons by parent container (representing a single question)
    var parentMap = new Map();
    for (var i = 0; i < allButtons.length; i++) {
      var btn = allButtons[i];
      if (btn.disabled || (btn.offsetParent === null && btn.offsetWidth === 0 && btn.offsetHeight === 0)) continue;
      var txtCheck = ((btn.innerText || btn.textContent || btn.value || '') + '').trim();
      if (txtCheck.length === 0 || txtCheck.length > 120) continue;

      var parent = btn.closest('.survey-step, .question-container, .question, .quiz-step, .step, fieldset, .answers, .options, .btn-group, .choices, [class*="question"], [class*="step"], [class*="survey"], [class*="quiz"]') || btn.parentElement;
      if (!parentMap.has(parent)) {
        parentMap.set(parent, []);
      }
      var list = parentMap.get(parent);
      if (list.indexOf(btn) === -1) list.push(btn);
    }

    var now = Date.now();
    var acted = false;

    parentMap.forEach(function(buttons, parent) {
      if (acted || buttons.length < 2) return;

      // Check if any button in this group is already selected/active
      var isAnyActive = buttons.some(function(b) {
        return b.classList.contains('active') || b.classList.contains('selected') || b.classList.contains('checked') || b.getAttribute('aria-selected') === 'true' || b.getAttribute('aria-pressed') === 'true';
      });
      if (isAnyActive) return;

      var qText = getQuestionContext(parent) || getQuestionContext(buttons[0]);
      var qKey = (qText || '') + '_' + buttons.length;

      // Anti-loop protection: don't click the exact same question group within 2.0 seconds
      if (window._cpaAnsweredQuestions[qKey] && (now - window._cpaAnsweredQuestions[qKey] < 2000)) {
        return;
      }

      var optionsList = buttons.map(function(b) {
        return {
          el: b,
          text: (b.innerText || b.textContent || b.value || b.getAttribute('aria-label') || '').trim(),
          value: b.getAttribute('data-value') || b.getAttribute('data-answer') || b.getAttribute('value') || ''
        };
      });

      var best = pickBestSurveyOption(qText, optionsList, ident);
      if (best && best.el) {
        window._cpaAnsweredQuestions[qKey] = now;
        var chosenText = best.text || best.value || 'Selected';
        logCpa('Intelligent Survey Button Clicked: "' + chosenText + '" for question: "' + qText.slice(0, 40) + '..."');
        showFloatingBadge('⚡ Survey: ' + chosenText.slice(0, 16));
        triggerClick(best.el);
        acted = true;
      }
    });

    return acted;
  }

  function handleRadioSurveys(ident) {
    var radios = Array.from(document.querySelectorAll('input[type=radio]'));
    var groups = {};
    for (var i = 0; i < radios.length; i++) {
      var r = radios[i];
      if (r.disabled || !isElementOrLabelVisible(r)) continue;
      var name = r.name || ('radio_group_' + i);
      if (!groups[name]) groups[name] = [];
      groups[name].push(r);
    }

    var answeredCount = 0;
    for (var g in groups) {
      var groupRadios = groups[g];
      var anyChecked = groupRadios.some(function(r) { return r.checked; });
      if (!anyChecked && groupRadios.length > 0) {
        var qText = getQuestionContext(groupRadios[0]);
        var optionsList = groupRadios.map(function(r) {
          var lbl = findVisibleLabelForInput(r);
          var labelText = lbl ? (lbl.innerText || lbl.textContent || '') : (r.parentElement ? (r.parentElement.innerText || r.parentElement.textContent || '') : '');
          return {
            el: r,
            labelEl: lbl,
            text: labelText.trim(),
            value: r.value || ''
          };
        });

        var best = pickBestSurveyOption(qText, optionsList, ident);
        if (best && best.el) {
          best.el.checked = true;
          best.el.dispatchEvent(new Event('input', { bubbles: true }));
          best.el.dispatchEvent(new Event('change', { bubbles: true }));
          best.el.dispatchEvent(new Event('click', { bubbles: true }));
          if (best.labelEl) {
            triggerClick(best.labelEl);
          } else {
            triggerClick(best.el);
          }
          logCpa('Survey Radio Selected: "' + (best.text || best.value) + '"');
          showFloatingBadge('⚡ Radio: ' + (best.text || best.value).slice(0, 16));
          answeredCount++;
        }
      }
    }
    return answeredCount;
  }

  function handleSelectSurveys(ident) {
    var selects = Array.from(document.querySelectorAll('select'));
    var selectAnswered = 0;

    for (var i = 0; i < selects.length; i++) {
      var sel = selects[i];
      if (sel.disabled || (sel.offsetParent === null && sel.offsetWidth === 0)) continue;
      if (sel.selectedIndex > 0 && sel.value && sel.value.trim().length > 0) continue;

      var qText = getQuestionContext(sel) || sel.name || sel.id || getAssociatedLabelText(sel) || '';
      var options = Array.from(sel.options);
      if (options.length <= 1) continue;

      var optionsList = options.map(function(opt, idx) {
        return {
          el: opt,
          index: idx,
          text: (opt.text || '').trim(),
          value: opt.value || ''
        };
      });

      var best = pickBestSurveyOption(qText, optionsList, ident);
      if (best && best.index !== undefined) {
        sel.selectedIndex = best.index;
        setNativeValue(sel, best.value || best.text);
        sel.dispatchEvent(new Event('input', { bubbles: true }));
        sel.dispatchEvent(new Event('change', { bubbles: true }));
        logCpa('Survey Dropdown Selected: "' + best.text + '"');
        showFloatingBadge('⚡ Select: ' + best.text.slice(0, 16));
        selectAnswered++;
      }
    }
    return selectAnswered;
  }

  function handleCheckboxes() {
    var checkboxes = document.querySelectorAll('input[type=checkbox]');
    var checkedCount = 0;
    for (var i = 0; i < checkboxes.length; i++) {
      var cb = checkboxes[i];
      if (cb.checked || cb.disabled || !isElementOrLabelVisible(cb)) continue;

      var lbl = findVisibleLabelForInput(cb);
      var info = ((cb.name || '') + ' ' + (cb.id || '') + ' ' + (cb.className || '') + ' ' + (cb.getAttribute('aria-label') || '') + ' ' + getAssociatedLabelText(cb)).toLowerCase();
      var parentText = (cb.parentElement ? (cb.parentElement.innerText || cb.parentElement.textContent || '') : '').toLowerCase();
      var isHoneypotCb = info.match(/honeypot|honey|trap|bot-field|do-not-check/);
      if (isHoneypotCb) continue;

      var isTermsOrRequired = cb.required || checkboxes.length <= 3 ||
        info.match(/agree|terms|condition|privacy|policy|optin|subscribe|age|18|consent|accept|rule|confirm|receive|notify|موافق|شروط|أوافق/) ||
        parentText.match(/agree|terms|condition|privacy|policy|18 years|opt-in|subscribe|rules|i accept|i agree|consent|confirm|موافق|شروط/);

      if (isTermsOrRequired) {
        cb.checked = true;
        cb.dispatchEvent(new Event('input', { bubbles: true }));
        cb.dispatchEvent(new Event('change', { bubbles: true }));
        cb.dispatchEvent(new Event('click', { bubbles: true }));
        if (lbl && !cb.checked) {
          triggerClick(lbl);
        }
        checkedCount++;
      }
    }
    return checkedCount;
  }

  function isHoneypot(el) {
    try {
      if (!el) return true;
      var t = (el.type || '').toLowerCase();
      if (t === 'hidden') return true;
      var style = window.getComputedStyle(el);
      if (style && (style.display === 'none' || style.visibility === 'hidden' || parseFloat(style.opacity) === 0)) return true;
      var r = el.getBoundingClientRect();
      if ((r.width === 0 && r.height === 0) || r.left < -2000 || r.top < -2000) return true;
      var nm = ((el.name || '') + ' ' + (el.id || '') + ' ' + (el.className || '')).toLowerCase();
      if (nm.match(/honeypot|honey|trap|bot-field|spam|do-not-fill|hidden-field/)) return true;
      if (el.hasAttribute && el.hasAttribute('tabindex') && el.getAttribute('tabindex') === '-1' && (el.value || '') === '') {
        if (nm.match(/website|url|company|fax|middle/)) return true;
      }
    } catch(e) {}
    return false;
  }

  function fillInputFields() {
    var ident = window._cpaIdentity;
    if (!ident) return 0;

    var inputs = Array.from(document.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=checkbox]):not([type=radio]), select, textarea'));
    // Expert: human tab-order — top-to-bottom, skip honeypots (bot traps)
    inputs = inputs.filter(function(el) { return !isHoneypot(el); });
    inputs.sort(function(a, b) {
      try {
        var ra = a.getBoundingClientRect(), rb = b.getBoundingClientRect();
        if (Math.abs(ra.top - rb.top) > 20) return ra.top - rb.top;
        return ra.left - rb.left;
      } catch(e) { return 0; }
    });
    // Persona facts memory: consistent age/gender across multi-page funnels
    window._cpaPersonaFacts = window._cpaPersonaFacts || {};
    if (ident.birthDate) {
      var __y = parseInt((ident.birthDate || '1995').substring(0, 4)) || 1995;
      window._cpaPersonaFacts.age = 2026 - __y;
      window._cpaPersonaFacts.gender = (ident.gender || '').toLowerCase();
    }
    var filled = 0;

    var phoneDigits = (ident.phone || '2125550199').replace(/\D/g, '');
    var dobParts = (ident.birthDate || '1995-06-15').split(/[-/]/);
    var dobYear = dobParts[0] && dobParts[0].length === 4 ? dobParts[0] : (dobParts[2] || '1995');
    var dobMonth = dobParts[0].length === 4 ? (dobParts[1] || '06') : (dobParts[0] || '06');
    var dobDay = dobParts[0].length === 4 ? (dobParts[2] || '15') : (dobParts[1] || '15');

    for (var i = 0; i < inputs.length; i++) {
      var el = inputs[i];
      if (el.value && el.value.trim().length > 0 && el.tagName !== 'SELECT') {
        continue;
      }

      var field = matchField(el);
      var value = null;

      if (field === 'firstName') value = ident.firstName;
      else if (field === 'lastName') value = ident.lastName;
      else if (field === 'fullName') value = ident.fullName;
      else if (field === 'email') value = ident.email;
      else if (field === 'phone') value = ident.phone;
      else if (field === 'phoneArea') value = phoneDigits.slice(0, 3);
      else if (field === 'phonePrefix') value = phoneDigits.slice(3, 6);
      else if (field === 'phoneLine') value = phoneDigits.slice(6, 10);
      else if (field === 'dobMonth') value = dobMonth;
      else if (field === 'dobDay') value = dobDay;
      else if (field === 'dobYear') value = dobYear;
      else if (field === 'address') value = ident.address;
      else if (field === 'city') value = ident.city;
      else if (field === 'state') value = ident.state;
      else if (field === 'postalCode') value = ident.postalCode;
      else if (field === 'country') value = ident.country;
      else if (field === 'birthDate') value = ident.birthDate;
      else if (field === 'gender') value = ident.gender;
      else if (field === 'username') value = ident.username;
      else if (field === 'password') value = ident.password;
      else if (field === 'cardNumber' && ident.cardNumber) value = ident.cardNumber.replace(/\s/g, '');
      else if (field === 'cardExpiry' && ident.cardExpiry) value = ident.cardExpiry;
      else if (field === 'cardCvv' && ident.cardCvv) value = ident.cardCvv;
      else if (field === 'cardHolder' && ident.cardHolder) value = ident.cardHolder;
      else if (field === 'cardExpMonth' && ident.cardExpiry) {
        var mMatch = ident.cardExpiry.match(/^(\d{1,2})/);
        value = mMatch ? mMatch[1].padStart(2, '0') : '12';
      }
      else if (field === 'cardExpYear' && ident.cardExpiry) {
        var yMatch = ident.cardExpiry.match(/\/(\d{2,4})$/);
        var yr = yMatch ? yMatch[1] : '28';
        value = yr.length === 2 ? '20' + yr : yr;
      }
      else if (field === 'bankName' && ident.bankName) value = ident.bankName;
      else if (field === 'age') value = String((window._cpaPersonaFacts && window._cpaPersonaFacts.age) || 29);

      // Smart Fallback for unlabeled or generic text/number inputs on forms
      if (!value && el.tagName !== 'SELECT') {
        var tLow = (el.type || 'text').toLowerCase();
        var maxLen = el.maxLength || 0;
        if (tLow === 'email') value = ident.email;
        else if (tLow === 'tel') value = ident.phone;
        else if (tLow === 'number' || maxLen === 5) value = ident.postalCode || '10001';
        else if (tLow === 'text' || tLow === 'search' || tLow === '') {
          if (inputs.length === 1) {
            value = ident.email;
          } else {
            var hasAnyEmailFilled = inputs.some(function(other) { return other.value && other.value.indexOf('@') !== -1; });
            if (!hasAnyEmailFilled && i === 0) value = ident.email;
            else if (i === 0) value = ident.firstName || ident.fullName;
            else if (i === 1) value = ident.lastName || ident.email;
            else value = ident.postalCode || ident.city || ident.address;
          }
        }
      }

      if (value && el.tagName === 'SELECT') {
        var opts = el.options;
        var valLower = value.toString().toLowerCase();
        for (var j = 0; j < opts.length; j++) {
          var optText = opts[j].text.toLowerCase();
          var optVal = (opts[j].value || '').toLowerCase();
          if (optText === valLower || optVal === valLower || optText.includes(valLower) || optVal.includes(valLower.slice(0, 3))) {
            el.selectedIndex = j;
            el.dispatchEvent(new Event('change', { bubbles: true }));
            filled++;
            break;
          }
        }
      } else if (value) {
        setNativeValue(el, value);
        filled++;
      }
    }
    return filled;
  }

  function findAndClickSubmit() {
    var now = Date.now();
    if (window._cpaLastSubmitClickAt && (now - window._cpaLastSubmitClickAt < 1800)) return false;

    var buttons = Array.from(document.querySelectorAll('button, input[type=submit], input[type=button], a[role=button], a.btn, a.button, div[role=button], [class*="btn"], [class*="submit"], [class*="cta"], [id*="submit"], [id*="continue"], [class*="continue"], [class*="next"]'));
    var keywords = [
      'continue', 'next', 'submit', 'claim', 'enter', 'get started', 'proceed', 'start', 'join',
      'sign up', 'agree', 'yes', 'participate', 'finish', 'go', 'win', 'reward', 'next question',
      'claim reward', 'confirm', 'verify', 'check', 'accept', 'ok', 'got it', 'unlock', 'send',
      'tap here', 'click here', 'get my', 'claim now', 'start now', 'start quiz', 'start survey',
      'متابعة', 'التالي', 'إرسال', 'تأكيد', 'ابدأ', 'موافق', 'اشترك', 'استمر', 'احصل'
    ];
    var negativeKeywords = ['no thanks', 'no, thanks', 'cancel', 'decline', 'back', 'previous', 'log out', 'sign out', 'unsubscribe'];

    for (var k = 0; k < buttons.length; k++) {
      var btn = buttons[k];
      if (btn.disabled || (btn.offsetParent === null && btn.offsetWidth === 0 && btn.offsetHeight === 0)) continue;

      var txt = ((btn.innerText || btn.textContent || '') + ' ' + (btn.value || '') + ' ' + (btn.getAttribute('aria-label') || '') + ' ' + (btn.id || '') + ' ' + (btn.className || '')).toLowerCase().trim();
      if (negativeKeywords.some(function(nk) { return txt.indexOf(nk) !== -1; })) continue;
      if (keywords.some(function(kw) { return txt.includes(kw); })) {
        window._cpaLastSubmitClickAt = now;
        logCpa('Auto-clicking action button: "' + (btn.innerText || btn.textContent || btn.value || '').trim().slice(0, 40) + '"');
        showFloatingBadge('⚡ Action: ' + (btn.innerText || btn.textContent || btn.value || 'Continue').trim().slice(0, 16) + '...');
        triggerClick(btn);
        return true;
      }
    }

    // Fallback: Submit form if available
    var forms = document.querySelectorAll('form');
    if (forms.length > 0) {
      for (var f = 0; f < forms.length; f++) {
        var submitBtn = forms[f].querySelector('[type=submit], button:not([type=button])');
        if (submitBtn && (submitBtn.offsetParent !== null || submitBtn.offsetWidth > 0)) {
          window._cpaLastSubmitClickAt = now;
          triggerClick(submitBtn);
          return true;
        }
      }
    }
    return false;
  }

  function handleSkipUpsells() {
    // 1. Close intrusive modals or promotional popups if blocking
    try {
      var closeBtns = document.querySelectorAll('.modal-close, .popup-close, [aria-label="Close"], .close-button, .btn-close, .dialog-close');
      for (var c = 0; c < closeBtns.length; c++) {
        if (closeBtns[c].offsetParent !== null) {
          triggerClick(closeBtns[c]);
          return true;
        }
      }
    } catch(e) {}

    // 2. Click skip/decline upsell links
    var skipKeywords = ['no thanks', 'skip', 'not interested', 'no thank you', 'skip this offer', 'continue without offer', 'decline', 'pass', 'maybe later', 'no, thanks', 'no, thank you', 'skip offer', 'i do not want this', 'continue to final step', 'skip deal', 'no, keep current'];
    var clickableElements = Array.from(document.querySelectorAll('a, button, input[type=button], span[role=button], div[role=button]'));
    for (var i = 0; i < clickableElements.length; i++) {
      var el = clickableElements[i];
      if (el.offsetParent === null) continue;
      var text = (el.innerText || el.textContent || el.getAttribute('aria-label') || el.value || '').toLowerCase().trim();
      if (skipKeywords.some(function(kw) { return text === kw || text.indexOf(kw) !== -1; })) {
        logCpa('Smart Upsell Skipped: "' + text.slice(0, 30) + '"');
        showFloatingBadge('⏭️ Skipped Offer');
        triggerClick(el);
        return true;
      }
    }
    return false;
  }

  function handleSignUpFields(ident) {
    var pwInputs = Array.from(document.querySelectorAll('input[type=password]'));
    var filledPw = 0;
    if (pwInputs.length > 0) {
      var generatedPassword = 'CpaPass@' + (ident.firstName || 'User') + '2026!';
      pwInputs.forEach(function(pw) {
        if (!pw.value || pw.value.trim().length === 0) {
          setNativeValue(pw, generatedPassword);
          filledPw++;
        }
      });
      if (filledPw > 0) {
        logCpa('Sign Up: Generated and filled secure password for ' + filledPw + ' fields');
        showFloatingBadge('👤 Sign Up Password Filled');
      }
    }
    return filledPw;
  }

  function handleOfferClick() {
    if (window._cpaOfferClicked) return false;

    // Strict category guard: Click is ONLY activated when selected in categories!
    var plan = window._cpaCategoryPlan || [];
    var rawCats = (window._cpaCategoriesRaw || '').toLowerCase();
    var isOfferClickCategoryEnabled = plan.some(function(p) { return p.id === 'offer_click'; }) ||
      rawCats.indexOf('offer_click') !== -1 ||
      rawCats.indexOf('النقرة') !== -1 ||
      rawCats.indexOf('نقر') !== -1;

    if (!isOfferClickCategoryEnabled) {
      return false;
    }

    var targetList = [];
    if (window._cpaActiveClickText && typeof window._cpaActiveClickText === 'string' && window._cpaActiveClickText.trim().length > 0) {
      targetList.push(window._cpaActiveClickText.trim().toLowerCase());
    }
    if (window._cpaClickTexts && Array.isArray(window._cpaClickTexts)) {
      for (var t = 0; t < window._cpaClickTexts.length; t++) {
        var str = (window._cpaClickTexts[t] || '').trim().toLowerCase();
        if (str && targetList.indexOf(str) === -1) {
          targetList.push(str);
        }
      }
    }
    // If no explicit targets specified by user or task, do not click random elements!
    if (targetList.length === 0) {
      return false;
    }

    function checkMatch(elementText, target) {
      if (!elementText || !target) return false;
      if (elementText.indexOf(target) !== -1) return true;
      var cleanEl = elementText.replace(/[^a-z0-9]/g, ' ').replace(/\s+/g, ' ').trim();
      var cleanTarget = target.replace(/[^a-z0-9]/g, ' ').replace(/\s+/g, ' ').trim();
      if (cleanEl.indexOf(cleanTarget) !== -1) return true;
      var tokens = cleanTarget.split(' ').filter(function(w) { return w.length >= 3; });
      if (tokens.length >= 2) {
        var allIn = tokens.every(function(tk) { return cleanEl.indexOf(tk) !== -1; });
        if (allIn) return true;
      }
      return false;
    }

    function scanAndClickOffer() {
      // Scan interactive and semantic elements: links, buttons, headers, cards
      var candidates = Array.from(document.querySelectorAll('a, button, [role=button], h1, h2, h3, h4, h5, p, span, div, strong, b, [class*="offer"], [class*="cta"], [class*="btn"], [class*="item"], [class*="card"]'));
      for (var i = 0; i < candidates.length; i++) {
        var el = candidates[i];
        if (el.offsetParent === null && el.offsetWidth === 0 && el.offsetHeight === 0) continue; // Skip hidden elements

        var txt = ((el.innerText || el.textContent || '') + ' ' + (el.getAttribute('aria-label') || '') + ' ' + (el.title || '')).toLowerCase().trim();
        if (!txt || txt.length < 2) continue;

        for (var k = 0; k < targetList.length; k++) {
          var targetWord = targetList[k];
          if (checkMatch(txt, targetWord)) {
            window._cpaOfferClicked = true;
            logCpa('🎯 [النقرة على العرض]: تم العثور على العرض الهدف: "' + targetWord + '"');
            showFloatingBadge('🎯 Clicked: ' + targetWord.slice(0, 16) + '...');

            var clickable = el.closest('a, button, [role=button]') || el;
            var href = clickable.href || (clickable.getAttribute ? clickable.getAttribute('href') : null);

            // If link opens in new tab (target="_blank"), modify to load in place or notify
            if (clickable.tagName === 'A' && clickable.target === '_blank') {
              clickable.target = '_self';
            }

            if (window.AndroidBridge && window.AndroidBridge.onOfferClicked) {
              try {
                window.AndroidBridge.onOfferClicked(targetWord, href || window.location.href);
              } catch(e) {}
            }

            triggerClick(clickable);

            // Force redirect if href is an external offer link and didn't fire
            if (href && href.startsWith('http') && href !== window.location.href) {
              setTimeout(function() {
                window.location.href = href;
              }, 400);
            }
            return true;
          }
        }
      }
      return false;
    }

    // Attempt 1: Direct scan in current visible DOM
    if (scanAndClickOffer()) return true;

    // Attempt 2: Dynamic JavaScript Menu / Drawer Triggering
    // On many sites, the offer list is rendered or shown via a JavaScript script or dropdown/toggle.
    if (!window._cpaMenuRevealed) {
      window._cpaMenuRevealed = true;

      // Check for inline JS functions that reveal menus/offers
      var jsFunctions = ['showOffers', 'toggleMenu', 'openMenu', 'openOffers', 'loadOffers', 'showList', 'displayOffers', 'renderOffers', 'unlockOffers'];
      for (var j = 0; j < jsFunctions.length; j++) {
        var fn = jsFunctions[j];
        if (typeof window[fn] === 'function') {
          try {
            logCpa('⚡ تشغيل سكريبت القائمة بالجافاسكريبت: ' + fn + '()');
            window[fn]();
            setTimeout(scanAndClickOffer, 350);
            return true;
          } catch(e) {}
        }
      }

      // Check for menu/dropdown buttons and toggles
      var menuToggles = document.querySelectorAll(
        'button.navbar-toggler, button.hamburger, .menu-toggle, .nav-toggle, [data-toggle="collapse"], [data-toggle="dropdown"], [aria-label*="menu" i], .dropdown-toggle, #menuBtn, #openMenu, button[id*="menu" i], button[class*="menu" i], [class*="offer-btn"], [class*="unlock-btn"], [id*="offer-btn"]'
      );
      for (var m = 0; m < menuToggles.length; m++) {
        var toggleBtn = menuToggles[m];
        if (toggleBtn.offsetParent !== null) {
          logCpa('⚡ فتح القائمة / النافذة لإظهار العروض المخفية');
          triggerClick(toggleBtn);
          setTimeout(scanAndClickOffer, 350);
          return true;
        }
      }
    }

    return false;
  }

  // --- Dynamic Cookie / GDPR Modal Bypass ---
  function dismissCookieBanners() {
    try {
      var cookieKeywords = ['accept all', 'allow all', 'agree', 'accept cookies', 'i accept', 'i agree', 'got it', 'understand', 'allow cookies'];
      var btns = Array.from(document.querySelectorAll('button, a[role=button], .cookie-btn, #cookie-btn, .consent-btn'));
      for (var b = 0; b < btns.length; b++) {
        var el = btns[b];
        if (el.offsetParent === null) continue;
        var txt = (el.innerText || el.textContent || '').toLowerCase().trim();
        if (cookieKeywords.some(function(k) { return txt === k || txt.startsWith(k); })) {
          logCpa('Bypassed cookie banner: ' + txt);
          triggerClick(el);
          return true;
        }
      }
    } catch(e) {}
    return false;
  }

  // --- Dynamic Page Action Map Generator (خريطة عمل الصفحة التلقائية) ---
  function generateAndReportActionMap() {
    try {
      var actionMap = [];
      var order = 1;

      // 1. Initial human scroll & view
      actionMap.push({
        order: order++,
        title: 'محاكاة استعراض الصفحة والتمرير الطبيعي',
        type: 'scroll',
        target: 'body',
        status: 'done',
        details: 'تمرير متدرج للتأكد من ظهور كافة العناصر'
      });

      // 2. Cookie banner check
      var hasCookieBanner = document.querySelector('[class*="cookie"], [id*="cookie"], [class*="consent"], [id*="consent"]');
      if (hasCookieBanner) {
        actionMap.push({
          order: order++,
          title: 'تخطي نافذة ملفات تعريف الارتباط (Cookie / GDPR)',
          type: 'click',
          target: 'cookie_banner',
          status: 'executing',
          details: 'إغلاق الإشعار للسماح بالوصول للأزرار والحقول'
        });
      }

      // 3. Offer Click check
      var rawCats = (window._cpaCategoriesRaw || '').toLowerCase();
      var plan = window._cpaCategoryPlan || [];
      var hasOfferClick = plan.some(function(p) { return p.id === 'offer_click'; }) ||
        rawCats.indexOf('offer_click') !== -1 || rawCats.indexOf('النقرة') !== -1 || rawCats.indexOf('نقر') !== -1;
      if (hasOfferClick && !window._cpaOfferClicked) {
        actionMap.push({
          order: order++,
          title: 'النقر على العرض الهدف (#1 في الأولوية)',
          type: 'click',
          target: window._cpaActiveClickText || 'Offer Link',
          status: 'executing',
          details: 'البحث عن نص العرض المحدد والنقر التلقائي للتحويل لموقع العرض'
        });
      }

      // 4. Questions / Survey / Quiz check
      var radios = Array.from(document.querySelectorAll('input[type=radio]'));
      var surveyBtns = Array.from(document.querySelectorAll('.survey-btn, .quiz-option, [data-choice], button:not([type=submit]):not([class*="submit"])'));
      if (radios.length >= 2 || surveyBtns.length >= 2) {
        var sampleQ = getQuestionContext(radios[0] || surveyBtns[0]) || 'سؤال استبيان تأهيلي';
        actionMap.push({
          order: order++,
          title: 'فهم وتكيّف سؤال الاستبيان: ' + (sampleQ.length > 25 ? sampleQ.slice(0, 25) + '...' : sampleQ),
          type: 'extract',
          target: 'survey_options',
          status: 'executing',
          details: 'استخراج منطوق السؤال واختيار الإجابة الأكثر ملاءمة للشخصية'
        });
      }

      // 5. Input fields check
      var inputs = Array.from(document.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=checkbox]):not([type=radio]), select, textarea'));
      if (inputs.length > 0) {
        actionMap.push({
          order: order++,
          title: 'ملء حقول البيانات (' + inputs.length + ' حقول مكتشفة)',
          type: 'fill',
          target: 'form_inputs',
          status: 'executing',
          details: 'إدراج البريد، الاسم، العنوان، الهاتف والرمز البريدي من شاشة المعلومات'
        });
      }

      // 6. Checkboxes (terms & majority age)
      var checkboxes = Array.from(document.querySelectorAll('input[type=checkbox]'));
      if (checkboxes.length > 0) {
        actionMap.push({
          order: order++,
          title: 'الموافقة على الشروط وتأكيد السن القانوني (18+)',
          type: 'check',
          target: 'checkboxes',
          status: 'executing',
          details: 'تحديد مربعات الشروط الإلزامية للمتابعة'
        });
      }

      // 7. Skip upsells check
      var hasSkip = Array.from(document.querySelectorAll('button, a, [role=button]')).some(function(b) {
        var t = (b.innerText || b.textContent || b.value || '').toLowerCase();
        return t.match(/no thanks|skip|not interested/);
      });
      if (hasSkip) {
        actionMap.push({
          order: order++,
          title: 'تخطي العروض الدعائية والرعاة (Skip / No Thanks)',
          type: 'click',
          target: 'skip_button',
          status: 'executing',
          details: 'النقر على زر التخطي لتفادي طلبات الدفع أو العروض الإضافية'
        });
      }

      // 8. Action button / Submit
      actionMap.push({
        order: order++,
        title: 'نقر زر المتابعة / إرسال التحويل (CTA Submit)',
        type: 'click',
        target: 'submit_button',
        status: 'pending',
        details: 'نقر زر الإرسال أو التالي للانتقال للمرحلة القادمة'
      });

      if (window.AndroidBridge && typeof window.AndroidBridge.onPageActionMapGenerated === 'function') {
        window.AndroidBridge.onPageActionMapGenerated(JSON.stringify(actionMap));
      }
    } catch(e) {}
  }

  // --- Main Execution Cycle ---

  window._cpaExecuteCycle = function() {
    var ident = window._cpaIdentity;
    if (!ident) return;

    var plan = window._cpaCategoryPlan || [];
    var rawCats = (window._cpaCategoriesRaw || '').toLowerCase();

    // 0. LOCKER & GATEKEEPER PROTECTION GUARD:
    // Only suspend form interactions if an ACTUAL CPA Content Locker script or iframe is present on the current page
    // AND we are not already inside the opened destination offer tab!
    var hasLockerScript = !!document.querySelector(
      'script[src*="script_include"], script[src*="alignmentfiles"], script[src*="cpagrip"], script[src*="filetrkr"], script[src*="ogads"], script[src*="cpabuild"]'
    );
    var hasLockerElement = !!document.querySelector(
      '#InlineBoxMainOuterLayer, #InlineBoxDiv, #the_box, iframe[src*="alignmentfiles"], iframe[src*="load_box"], iframe[src*="cpagrip"], iframe[src*="ogads"], iframe[src*="cpabuild"], iframe[src*="locker"], div[class*="cpa_locker"], div[id*="cpa_locker"], div[class*="locker-offers"]'
    );

    if ((hasLockerScript || hasLockerElement) && !window.__cpa_locker_offer_clicked && !window._cpaIsOfferTab) {
      logCpa('Content Locker is active on this page. Background page form interactions are suspended until offer click.');
      return;
    }

    // Dismiss cookie banners on the page
    dismissCookieBanners();

    // Generate dynamic page action map for user inspection on offer page
    generateAndReportActionMap();

    var hasOfferClickPlan = plan.some(function(p) { return p.id === 'offer_click'; }) ||
      rawCats.indexOf('offer_click') !== -1 ||
      rawCats.indexOf('النقرة') !== -1 ||
      rawCats.indexOf('نقر') !== -1;
    var hasSkipPlan = plan.some(function(p) { return p.id === 'skip_upsells'; });
    var hasSignUpPlan = plan.some(function(p) { return p.id === 'sign_up'; });

    // 0. PRIORITY #1 - OFFER CLICK only runs on landing bridge pages when selected in categories (not on destination form/survey pages)
    var pageHasFormsOrQuestions = document.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=button]), select, textarea').length > 0;
    if (hasOfferClickPlan && !window._cpaOfferClicked && !window._cpaIsOfferTab && !pageHasFormsOrQuestions) {
      if (handleOfferClick()) {
        logCpa('Offer Click executed. Holding cycle for destination offer site to load.');
        return;
      }
    }

    // 0.5. Handle Skip Upsells if present on page
    if (hasSkipPlan || plan.length === 0) {
      if (handleSkipUpsells()) return;
    }

    // 1. Fill Text / Contact Inputs
    var nowTs = Date.now();
    if (window._cpaStageWaitUntil && nowTs < window._cpaStageWaitUntil) {
      var remSec = Math.ceil((window._cpaStageWaitUntil - nowTs) / 1000);
      reportHumanAction('⏳ انتظار بشري ذكي بين المراحل (' + remSec + ' ثانية) للقراءة والتثبت قبل المتابعة...');
      return;
    }

    // Detect if DOM stage / visible question changed; if so, apply a natural human reading pause before interacting
    var domSig = (window.location.href || '') + '|' + document.querySelectorAll('input:not([type=hidden]), select, textarea').length + '|' + ((document.title || '').slice(0, 30));
    if (window._cpaHumanConfig && window._cpaHumanConfig.enabled && window._cpaHumanConfig.readingPause && window._cpaLastDomSig !== domSig) {
      var isInitialPass = !window._cpaLastDomSig;
      window._cpaLastDomSig = domSig;
      var bodyLen = ((document.body && document.body.innerText) ? document.body.innerText.length : 120);
      var pauseMs = isInitialPass
        ? computeHumanReadingDelayMs(Math.min(bodyLen, 140))
        : Math.max(
            computeHumanReadingDelayMs(Math.min(bodyLen, 200)),
            Math.floor(((window._cpaHumanConfig.stageWaitMinSec || 2) * 550) * getProfileSpeedFactor())
          );
      window._cpaStageWaitUntil = nowTs + pauseMs;
      reportHumanAction('👁️ قراءة واستيعاب محتوى المرحلة الحالية بشرياً (' + (pauseMs / 1000).toFixed(1) + ' ثانية)...');
      setTimeout(function() {
        if (typeof window._cpaExecuteCycle === 'function') window._cpaExecuteCycle();
      }, pauseMs + 40);
      return;
    }

    var filled = fillInputFields();

    // 2. Handle Password / Sign Up credentials if required
    if (hasSignUpPlan || document.querySelector('input[type=password]')) {
      filled += handleSignUpFields(ident);
    }

    // 3. Check Terms / Consent Checkboxes
    var checked = handleCheckboxes();

    // 4. Handle Radio Button Surveys
    var radios = handleRadioSurveys(ident);

    // 5. Handle Dropdown Surveys
    var selects = handleSelectSurveys(ident);

    // 6. Handle Button / Card / Tile Surveys
    var buttonSurveyHandled = handleButtonSurveys(ident);

    if (filled > 0 || checked > 0 || radios > 0 || selects > 0) {
      logCpa('Cycle update: filled ' + filled + ', checked ' + checked + ', radios ' + radios + ', selects ' + selects);
      var submitWaitMs = (window._cpaHumanConfig && window._cpaHumanConfig.enabled)
        ? Math.floor((850 + Math.random() * 950) * getProfileSpeedFactor())
        : 650;
      reportHumanAction('✍️ تم إدخال البيانات بشرياً (' + (filled + radios + selects) + ' عناصر) — مراجعة سريعة (' + (submitWaitMs / 1000).toFixed(1) + 's) قبل النقر على متابعة...');
      // Human review pause before clicking Next/Submit
      setTimeout(function() {
        if (findAndClickSubmit()) {
          // Set inter-stage transition wait so the next funnel stage isn't clicked instantaneously
          if (window._cpaHumanConfig && window._cpaHumanConfig.enabled) {
            var minW = (window._cpaHumanConfig.stageWaitMinSec || 3) * 1000;
            var maxW = Math.max(minW, (window._cpaHumanConfig.stageWaitMaxSec || 6) * 1000);
            var stagePause = Math.floor((minW * 0.55) + Math.random() * ((maxW - minW) * 0.65));
            window._cpaStageWaitUntil = Date.now() + stagePause;
            reportHumanAction('⏳ انتقال بين المراحل: انتظار بشري (' + (stagePause / 1000).toFixed(1) + ' ثانية) لاستقرار المرحلة التالية...');
          }
        }
      }, submitWaitMs);
    } else if (!buttonSurveyHandled) {
      // Check if all visible non-honeypot fields are filled (or if this is a pre-lander / single CTA step with 0 inputs)
      var rawInputs = Array.from(document.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=checkbox]):not([type=radio]), textarea'));
      var visibleInputs = rawInputs.filter(function(i) { return !isHoneypot(i); });
      var allFilled = visibleInputs.length === 0 || visibleInputs.every(function(i) { return i.value && i.value.trim().length > 0; });
      if (allFilled) {
        findAndClickSubmit();
      }
    }
  };

  // Run cycle immediately
  window._cpaExecuteCycle();

  // Install continuous observer and interval if not already running
  if (!window._cpaObserverInstalled) {
    window._cpaObserverInstalled = true;
    showFloatingBadge('⚡ Smart Auto-Pilot: Active');

    setInterval(function() {
      if (typeof window._cpaExecuteCycle === 'function') {
        window._cpaExecuteCycle();
      }
    }, 1100);

    var obs = new MutationObserver(function() {
      if (typeof window._cpaExecuteCycle === 'function') {
        window._cpaExecuteCycle();
      }
      // Closed-loop reactive link: notify Central Brain whenever DOM mutates (SPA step change / new question)
      if (typeof window._cpaRunPageAnalyzer === 'function') {
        clearTimeout(window._cpaMutationAnalyzeTimer);
        window._cpaMutationAnalyzeTimer = setTimeout(function() {
          try { window._cpaRunPageAnalyzer(); } catch(e) {}
        }, 700);
      }
    });

    if (document.body) {
      obs.observe(document.body, { childList: true, subtree: true });
    }
  }
})();
true;
        """.trimIndent()
    }

    fun buildHumanBehaviorScript(): String {
        return """
(function() {
  // Gentle human presence without disruptive page movement
  try {
    var x = Math.floor(Math.random() * 200 + 100);
    var y = Math.floor(Math.random() * 200 + 100);
    document.dispatchEvent(new MouseEvent('mousemove', { clientX: x, clientY: y, bubbles: true }));
  } catch(e) {}
})();
true;
        """.trimIndent()
    }

    /**
     * Smart human simulation: natural reading scroll + mouse wander + hover awareness.
     * Does NOT click — pure awareness/exploration so bot-detectors see human rhythm.
     */
    fun buildAwareHumanSimulationScript(): String {
        return """
(function() {
  try {
    if (window.__cpa_aware_sim_running) return 'already';
    window.__cpa_aware_sim_running = true;
    var steps = 2 + Math.floor(Math.random() * 3);
    var i = 0;
    // 1. Mouse wander: 3-5 natural moves
    var mouseMoves = 0;
    var mouseTimer = setInterval(function() {
      try {
        var x = Math.floor(Math.random() * (window.innerWidth - 100)) + 50;
        var y = Math.floor(Math.random() * (window.innerHeight - 100)) + 50;
        ['mousemove','mouseover'].forEach(function(t) {
          document.dispatchEvent(new MouseEvent(t, { clientX: x, clientY: y, bubbles: true, view: window }));
        });
      } catch(e) {}
      if (++mouseMoves >= 4) clearInterval(mouseTimer);
    }, 700 + Math.floor(Math.random() * 600));
    // 2. Reading scroll: gradual down with pauses, occasional look-back
    function doScroll() {
      if (i >= steps) { window.__cpa_aware_sim_running = false; return; }
      var amt = 120 + Math.floor(Math.random() * 220);
      try { window.scrollBy({ top: amt, behavior: 'smooth' }); } catch(e) { window.scrollBy(0, amt); }
      i++;
      var pause = 900 + Math.floor(Math.random() * 1600);
      setTimeout(function() {
        if (Math.random() < 0.25) { try { window.scrollBy({ top: -60, behavior: 'smooth' }); } catch(e){} }
        setTimeout(doScroll, 500);
      }, pause);
    }
    setTimeout(doScroll, 600);
    // 3. Hover primary CTA without clicking (awareness signal)
    setTimeout(function() {
      try {
        var cta = document.querySelector('button:not([disabled]), a.btn, [class*="cta"]');
        if (cta && cta.offsetParent !== null) {
          var r = cta.getBoundingClientRect();
          cta.dispatchEvent(new MouseEvent('mouseover', { clientX: r.left + r.width/2, clientY: r.top + 10, bubbles: true }));
        }
      } catch(e) {}
    }, 1500);
    return 'started';
  } catch(e) { return 'err:' + e.message; }
})();
true;
        """.trimIndent()
    }

    /**
     * Human typing: types value char-by-char with random 30-90ms delays,
     * firing proper input events so React/Angular trackers accept it.
     * @param selector CSS selector of the field, @param value text to type
     */
    fun buildHumanTypingScript(selector: String, value: String): String {
        val safeSel = selector.replace("'", "\\'").replace("\n", "")
        val safeVal = org.json.JSONObject.quote(value)
        return """
(function() {
  try {
    var el = document.querySelector('$safeSel');
    if (!el) return 'not_found';
    var text = $safeVal;
    el.focus();
    el.value = '';
    el.dispatchEvent(new Event('focus', { bubbles: true }));
    var idx = 0;
    // Occasional typo + correction for ultra-human signal (5% chance)
    var makeTypo = Math.random() < 0.05 && text.length > 6;
    var typoAt = makeTypo ? (2 + Math.floor(Math.random() * (text.length - 4))) : -1;
    function typeNext() {
      if (idx >= text.length) {
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        el.dispatchEvent(new Event('blur', { bubbles: true }));
        return 'done';
      }
      var ch = text[idx];
      if (idx === typoAt) {
        // type wrong char then backspace
        var wrong = (ch === 'a') ? 's' : 'a';
        el.value += wrong;
        el.dispatchEvent(new Event('input', { bubbles: true }));
        setTimeout(function() {
          el.value = el.value.slice(0, -1);
          el.dispatchEvent(new Event('input', { bubbles: true }));
          el.value += ch;
          el.dispatchEvent(new Event('input', { bubbles: true }));
          idx++;
          setTimeout(typeNext, 60 + Math.floor(Math.random() * 70));
        }, 120 + Math.floor(Math.random() * 150));
        return;
      }
      // Native setter for framework compat
      try {
        var proto = el.tagName === 'SELECT' ? window.HTMLSelectElement.prototype : window.HTMLInputElement.prototype;
        var setter = Object.getOwnPropertyDescriptor(proto, 'value')?.set;
        var cur = el.value + ch;
        if (setter) setter.call(el, cur); else el.value = cur;
      } catch(e) { el.value += ch; }
      el.dispatchEvent(new Event('input', { bubbles: true }));
      idx++;
      setTimeout(typeNext, 30 + Math.floor(Math.random() * 60));
    }
    typeNext();
    return 'typing';
  } catch(e) { return 'err:' + e.message; }
})();
true;
        """.trimIndent()
    }

    fun buildCompletionDetectorScript(keywords: List<String>): String {
        val kwArray = keywords.joinToString(",") { "'${it.trim().lowercase().replace("'", "\\'")}'" }
        return """
(function() {
  var keywords = [$kwArray];
  var completionReported = false;

  function checkCompletion() {
    if (completionReported) return;
    // Never fire completion while interactive form fields or survey questions remain unfilled on the page!
    var visibleEmptyInputs = Array.from(document.querySelectorAll('input[type="text"], input[type="email"], input[type="tel"], input[type="password"]')).filter(function(el) {
      return el.offsetParent !== null && (!el.value || el.value.trim().length === 0);
    });
    var visibleRadios = Array.from(document.querySelectorAll('input[type="radio"]')).filter(function(el) {
      return el.offsetParent !== null || (el.parentElement && el.parentElement.offsetParent !== null);
    });
    if (visibleEmptyInputs.length > 0 || visibleRadios.length >= 2) return;

    var bodyText = (document.body ? document.body.innerText : '').toLowerCase();
    var title = document.title.toLowerCase();

    for (var i = 0; i < keywords.length; i++) {
      var kw = keywords[i];
      if (kw && kw.length >= 6 && (bodyText.includes(kw) || title.includes(kw))) {
        completionReported = true;
        if (window.AndroidBridge && window.AndroidBridge.onTaskCompleted) {
          window.AndroidBridge.onTaskCompleted(kw, window.location.href);
        }
        break;
      }
    }
  }

  setTimeout(checkCompletion, 1800);
  window.addEventListener('load', function() { setTimeout(checkCompletion, 1200); });
  var obs = new MutationObserver(function() { setTimeout(checkCompletion, 700); });
  if (document.body) {
    obs.observe(document.body, { childList: true, subtree: true, characterData: true });
  }
})();
true;
        """.trimIndent()
    }

    fun buildOfferClickScript(clickTexts: List<String>, activeTargetText: String? = null): String {
        val clickTextsArray = JSONArray().apply {
            clickTexts.filter { it.isNotBlank() }.forEach { put(it) }
        }.toString()
        val activeTargetJson = if (activeTargetText.isNullOrBlank()) "null" else JSONObject.quote(activeTargetText)

        return """
(function() {
  var targetList = [];
  var active = $activeTargetJson;
  var allTexts = $clickTextsArray;
  if (active && active.trim().length > 0) targetList.push(active.trim().toLowerCase());
  for (var i = 0; i < allTexts.length; i++) {
    var s = (allTexts[i] || '').trim().toLowerCase();
    if (s && targetList.indexOf(s) === -1) targetList.push(s);
  }
  if (targetList.length === 0) targetList = ['walmart', 'gift card', '$1000', 'claim reward'];

  function clickTarget() {
    var candidates = Array.from(document.querySelectorAll('a, button, [role=button], h1, h2, h3, h4, h5, p, span, div, strong, b'));
    for (var i = 0; i < candidates.length; i++) {
      var el = candidates[i];
      if (el.offsetParent === null) continue;
      var txt = ((el.innerText || el.textContent || '') + ' ' + (el.getAttribute('aria-label') || '')).toLowerCase().trim();
      if (!txt || txt.length < 2) continue;

      for (var k = 0; k < targetList.length; k++) {
        var kw = targetList[k];
        if (txt.includes(kw)) {
          var clickable = el.closest('a, button, [role=button]') || el;
          var href = clickable.href || (clickable.getAttribute ? clickable.getAttribute('href') : null);
          if (clickable.tagName === 'A' && clickable.target === '_blank') clickable.target = '_self';
          if (window.AndroidBridge && window.AndroidBridge.onOfferClicked) {
            window.AndroidBridge.onOfferClicked(kw, href || window.location.href);
          }
          clickable.click();
          if (href && href.startsWith('http') && href !== window.location.href) {
            setTimeout(function() { window.location.href = href; }, 600);
          }
          return true;
        }
      }
    }
    return false;
  }

  setTimeout(clickTarget, 800);
  window.addEventListener('load', function() { setTimeout(clickTarget, 600); });
})();
true;
        """.trimIndent()
    }

    /**
     * Autonomous Deep DOM Analyzer Script.
     * Evaluates the active page structure, identifies the funnel stage / category,
     * and sends a comprehensive analysis report back to AndroidBridge.onPageAnalyzed().
     */
    fun buildPageAnalyzerScript(clickTexts: List<String> = emptyList(), activeClickText: String? = null): String {
        val clickTextsArray = JSONArray().apply {
            clickTexts.filter { it.isNotBlank() }.forEach { put(it) }
        }.toString()
        val activeTargetJson = if (activeClickText.isNullOrBlank()) "null" else JSONObject.quote(activeClickText)

        return """
(function() {
  function executeAnalyzer() {
  try {
    var url = window.location.href;
    var title = document.title || '';
    var bodyText = (document.body ? document.body.innerText : '').toLowerCase();

    // 1. Scan Inputs & Forms
    var allInputs = Array.from(document.querySelectorAll('input:not([type=hidden])'));
    var emailFields = 0;
    var textFields = 0;
    var passwordFields = 0;
    var checkboxes = Array.from(document.querySelectorAll('input[type=checkbox]'));
    var radios = Array.from(document.querySelectorAll('input[type=radio]'));
    var selects = Array.from(document.querySelectorAll('select'));
    var buttons = Array.from(document.querySelectorAll('button, [role=button], input[type=submit], input[type=button]'));

    allInputs.forEach(function(i) {
      var t = (i.type || 'text').toLowerCase();
      var meta = ((i.name || '') + ' ' + (i.id || '') + ' ' + (i.placeholder || '') + ' ' + (i.getAttribute('aria-label') || '')).toLowerCase();
      if (t === 'email' || meta.match(/email|e-mail/)) {
        emailFields++;
      } else if (t === 'password') {
        passwordFields++;
      } else if (t === 'text' || t === 'tel' || t === 'number') {
        textFields++;
      }
    });

    // 2. Scan for Skip / Upsell Buttons ("No thanks", "Skip", "Not interested")
    var hasSkipButtons = false;
    buttons.forEach(function(b) {
      var txt = ((b.innerText || b.textContent || '') + ' ' + (b.value || '')).toLowerCase().trim();
      if (txt.match(/no thanks|no, thanks|skip|not interested|pass|continue without/)) {
        hasSkipButtons = true;
      }
    });

    // 3. Scan for Priority #1 Offer Click Candidates
    var hasOfferCandidate = false;
    var targets = [];
    var activeTarget = $activeTargetJson;
    var configuredTexts = $clickTextsArray;
    if (activeTarget && activeTarget.trim().length > 0) targets.push(activeTarget.toLowerCase().trim());
    for (var k = 0; k < configuredTexts.length; k++) {
      if (configuredTexts[k] && configuredTexts[k].trim().length > 0) {
        targets.push(configuredTexts[k].toLowerCase().trim());
      }
    }

    if (targets.length > 0) {
      var clickableElements = Array.from(document.querySelectorAll('a, button, [role=button], h1, h2, h3, h4, [class*="offer"], [class*="cta"], [class*="btn"]'));
      for (var c = 0; c < clickableElements.length; c++) {
        var elTxt = ((clickableElements[c].innerText || clickableElements[c].textContent || '')).toLowerCase().trim();
        if (elTxt.length > 2) {
          for (var tg = 0; tg < targets.length; tg++) {
            if (elTxt.indexOf(targets[tg]) !== -1) {
              hasOfferCandidate = true;
              break;
            }
          }
        }
        if (hasOfferCandidate) break;
      }
    }

    // 4. Scan for Confirmation / Thank-You Indicators (strict final-page check — never trigger when inputs/questions remain)
    var unfilledCount = 0;
    var filledCount = 0;
    allInputs.forEach(function(inp) {
      var t = (inp.type || 'text').toLowerCase();
      if (t !== 'submit' && t !== 'button' && t !== 'checkbox' && t !== 'radio' && inp.offsetParent !== null) {
        if (inp.value && inp.value.trim().length > 0) filledCount++;
        else unfilledCount++;
      }
    });

    // Extract active question text if on a survey/quiz page
    var currentQuestionText = '';
    try {
      var qCandidates = document.querySelectorAll('h1, h2, h3, h4, legend, [class*="question" i], [id*="question" i], .survey-prompt, .quiz-title');
      for (var qi = 0; qi < qCandidates.length; qi++) {
        var qEl = qCandidates[qi];
        if (qEl && qEl.offsetParent !== null) {
          var qTxt = (qEl.innerText || qEl.textContent || '').trim().replace(/\s+/g, ' ');
          if (qTxt.length >= 6 && qTxt.length <= 160 && (qTxt.indexOf('?') !== -1 || radios.length > 0 || selects.length > 0 || qEl.className.toLowerCase().indexOf('question') !== -1)) {
            currentQuestionText = qTxt;
            break;
          }
        }
      }
    } catch(qe) {}

    // Extract primary visible CTA button text
    var primaryCtaText = '';
    try {
      for (var bi2 = 0; bi2 < buttons.length; bi2++) {
        var bEl = buttons[bi2];
        if (bEl && bEl.offsetParent !== null) {
          var bTxt = ((bEl.innerText || bEl.textContent || bEl.value || '') + '').trim().replace(/\s+/g, ' ');
          if (bTxt.length >= 2 && bTxt.length <= 40 && /continue|next|submit|claim|start|verify|confirm|proceed|get|enter|unlock|yes|متابعة|استمرار|إرسال|تأكيد|ابدأ/i.test(bTxt)) {
            primaryCtaText = bTxt;
            break;
          }
        }
      }
    } catch(be) {}

    var isConfirmation = false;
    var matchedConfirm = '';
    var hasRemainingInteractiveSteps = (unfilledCount > 0) || (radios.length > 0) || (selects.length > 0);
    var confirmKeywords = [
      'thank you for completing', 'thank you for participating', 'thank you for your submission',
      'submission received', 'order received', 'claim confirmed', 'reward credited',
      'entry received', 'entry confirmed', 'survey completed', 'successfully registered',
      'registration complete', 'account created', 'ticket number', 'responses recorded',
      'successfully submitted', 'check your inbox'
    ];
    if (!hasRemainingInteractiveSteps) {
      for (var kw = 0; kw < confirmKeywords.length; kw++) {
        if (bodyText.indexOf(confirmKeywords[kw]) !== -1 || title.toLowerCase().indexOf(confirmKeywords[kw]) !== -1) {
          isConfirmation = true;
          matchedConfirm = confirmKeywords[kw];
          break;
        }
      }
      if (!isConfirmation) {
        var urlLow = url.toLowerCase();
        if (urlLow.match(/(\/thank[-_]?you|\/success|\/complete|\/completed|\/confirmed|\/finish)/)) {
          isConfirmation = true;
          matchedConfirm = 'url-signal';
        }
      }
    }

    // 5. Scan for CPA Content Lockers
    var hasLocker = false;
    var lockerScripts = document.querySelectorAll('script[src*="cpa"], script[src*="cpagrip"], script[src*="alignmentfiles"], script[src*="locker"], script[src*="ogads"]');
    var lockerIframes = document.querySelectorAll('iframe[src*="cpa"], iframe[src*="cpagrip"], iframe[src*="alignmentfiles"], iframe[src*="locker"], iframe[class*="locker"], iframe[id*="locker"]');
    var lockerContainers = document.querySelectorAll('#cpa_locker, .cpa_locker, [id*="locker"], [class*="locker-offers"], [class*="offer-list"], [class*="cpa-offers"]');
    if (lockerScripts.length > 0 || lockerIframes.length > 0 || lockerContainers.length > 0 || bodyText.includes('complete an offer below') || bodyText.includes('locked content') || bodyText.includes('complete a survey below') || url.includes('alignmentfiles') || url.includes('cpagrip')) {
      hasLocker = true;
    }

    // 5b. SMART: Captcha / Block / Loading awareness (incl. Shadow DOM + iframes)
    var hasCaptcha = false; var captchaKeyword = '';
    var isBlocked = false; var blockKeyword = '';
    var isLoadingPage = false; var loadingKeyword = '';
    try {
      var captchaEls = document.querySelectorAll('iframe[src*="recaptcha"], iframe[src*="hcaptcha"], iframe[src*="captcha"], iframe[src*="turnstile"], div[class*="g-recaptcha"], div[class*="h-captcha"], div[id*="captcha"], [data-sitekey]');
      if (captchaEls.length > 0) { hasCaptcha = true; captchaKeyword = 'captcha-element'; }
      var captchaPats = ['captcha', 'recaptcha', 'hcaptcha', 'turnstile', "i'm not a robot", 'select all images', 'verify you are human'];
      for (var ci = 0; ci < captchaPats.length; ci++) { if (bodyText.indexOf(captchaPats[ci]) !== -1) { hasCaptcha = true; captchaKeyword = captchaPats[ci]; break; } }
      var blockPats = ['access denied', 'forbidden', 'your ip has been blocked', 'suspicious activity', 'unusual traffic', 'attention required', 'error 1020', 'ip banned', 'blocked'];
      for (var bi = 0; bi < blockPats.length; bi++) { if (bodyText.indexOf(blockPats[bi]) !== -1 || title.toLowerCase().indexOf(blockPats[bi]) !== -1) { isBlocked = true; blockKeyword = blockPats[bi]; break; } }
      var loadPats = ['just a moment', 'verifying your browser', 'checking your browser', 'please wait', 'redirecting', 'loading'];
      for (var li = 0; li < loadPats.length; li++) { if (bodyText.indexOf(loadPats[li]) !== -1 && bodyText.length < 800) { isLoadingPage = true; loadingKeyword = loadPats[li]; break; } }
      // Shadow DOM host count — pages hiding forms inside shadow roots
      var shadowHosts = 0;
      try { var allEls = document.querySelectorAll('*'); for (var si = 0; si < Math.min(allEls.length, 400); si++) { if (allEls[si].shadowRoot) shadowHosts++; } } catch(se) {}
      var iframeCount = document.querySelectorAll('iframe').length;
      var visibleBtnCount = buttons.filter(function(b){ try { return b.offsetParent !== null; } catch(e){ return true; } }).length;
    } catch(smartErr) { var shadowHosts = 0; var iframeCount = 0; var visibleBtnCount = buttons.length; }

    // 6. Intelligent Category & Stage Classification
    var category = 'general';
    var confidence = 70;
    var summary = 'Standard Web Page';
    var nextAction = 'Observe & wait';

    if (isConfirmation) {
      category = 'completion_confirm';
      confidence = 98;
      summary = 'Confirmation / Thank You Stage Detected! Reward claimed.';
      nextAction = 'Record lead conversion and proceed to next offer';
    } else if (hasLocker) {
      category = 'content_locker';
      confidence = 97;
      summary = 'Content / Link Locker Detected (Waiting for offers to auto-click)';
      nextAction = 'Wait for locker offers, auto-click priority matching offer, and switch to new tab';
    } else if (hasOfferCandidate && (url.includes('blogspot') || bodyText.includes('offer') || buttons.length <= 4)) {
      category = 'offer_click';
      confidence = 95;
      summary = 'Landing Bridge with Priority #1 Offer Target Detected!';
      nextAction = 'Execute Offer Click to bridge into destination offer funnel';
    } else if (hasSkipButtons) {
      category = 'skip_upsells';
      confidence = 92;
      summary = 'Co-Reg / Sponsor Upsell Wall Detected';
      nextAction = 'Auto-click No Thanks / Skip to bypass payment requirement';
    } else if (radios.length >= 2 || selects.length >= 1 || document.querySelectorAll('.survey-btn, .quiz-option, [data-choice]').length >= 2) {
      category = 'survey_quiz';
      confidence = 90;
      summary = 'Interactive Survey / Quiz Questionnaire Detected (' + radios.length + ' radios, ' + selects.length + ' dropdowns)';
      nextAction = 'Answer questions consistently matching persona demographics';
    } else if (emailFields >= 1 && textFields <= 2) {
      category = 'email_submit';
      confidence = 94;
      summary = 'High-Value Email Opt-In / SOI Lead Form Detected';
      nextAction = 'Fill verified persona email and submit';
    } else if (textFields >= 3) {
      category = 'lead_gen';
      confidence = 88;
      summary = 'Full Contact Info / Shipping Form Detected (' + textFields + ' fields)';
      nextAction = 'Fill complete contact persona details (Name, Address, Phone, Zip)';
    } else if (checkboxes.length >= 1 && checkboxes.some(function(cb) { return !cb.checked; })) {
      category = 'terms_agreement';
      confidence = 85;
      summary = 'Consent & 18+ Age Checkboxes Pending Agreement';
      nextAction = 'Check all mandatory terms and consent checkboxes';
    } else {
      category = 'sweepstakes';
      confidence = 75;
      summary = 'Offer Funnel Interactive Page';
      nextAction = 'Auto-detect active controls and advance';
    }

    // 6b. SMART confidence adjustment: penalize weak/ambiguous signals, boost strong ones
    try {
      if (typeof shadowHosts !== 'undefined' && shadowHosts > 0 && confidence > 55) confidence -= 10;
      if (typeof iframeCount !== 'undefined' && iframeCount >= 4 && category !== 'content_locker') confidence -= 5;
      if (allInputs.length === 0 && buttons.length === 0) confidence = Math.min(confidence, 45);
      if (hasCaptcha || isBlocked) confidence = Math.max(confidence, 88);
      if (isConfirmation && matchedConfirm) confidence = Math.min(99, confidence + 3);
    } catch(adjErr) {}

    // 6c. Compute structural DOM signature & funnel stage index for Closed-Loop Brain
    var isRedirectBridge = false;
    try {
      var uLow = url.toLowerCase();
      if ((uLow.indexOf('.php?') !== -1 || uLow.indexOf('show.php') !== -1 || uLow.indexOf('click.php') !== -1 || uLow.indexOf('go.php') !== -1 || uLow.indexOf('redirect') !== -1) && allInputs.length === 0 && radios.length === 0 && bodyText.length < 900) {
        isRedirectBridge = true;
      }
    } catch(re) {}

    var funnelStageIndex = 1;
    if (isConfirmation) funnelStageIndex = 6;
    else if (hasSkipButtons) funnelStageIndex = 5;
    else if (emailFields > 0 || textFields > 0) funnelStageIndex = 4;
    else if (radios.length > 0 || selects.length > 0 || currentQuestionText.length > 0) funnelStageIndex = 3;
    else if (hasLocker) funnelStageIndex = 2;
    else funnelStageIndex = 1;

    var domSignature = [
      category,
      allInputs.length,
      filledCount,
      radios.length,
      selects.length,
      buttons.length,
      currentQuestionText.slice(0, 24),
      primaryCtaText.slice(0, 18)
    ].join('|');

    var report = {
      url: url,
      title: title,
      detectedCategory: category,
      confidence: confidence,
      summary: summary,
      fieldsCount: allInputs.length,
      emailFields: emailFields,
      textFields: textFields,
      passwordFields: passwordFields,
      checkboxesCount: checkboxes.length,
      radioGroupsCount: Math.ceil(radios.length / 2),
      selectCount: selects.length,
      buttonsCount: buttons.length,
      hasSkipButtons: hasSkipButtons,
      hasLocker: hasLocker,
      hasOfferClickCandidate: hasOfferCandidate,
      isConfirmationPage: isConfirmation,
      recommendedNextAction: nextAction,
      domSignature: domSignature,
      currentQuestionText: currentQuestionText,
      unfilledRequiredCount: unfilledCount,
      filledFieldsCount: filledCount,
      primaryCtaText: primaryCtaText,
      isRedirectBridge: isRedirectBridge,
      funnelStageIndex: funnelStageIndex,
      hasCaptcha: (typeof hasCaptcha !== 'undefined') ? hasCaptcha : false,
      captchaKeyword: (typeof captchaKeyword !== 'undefined') ? captchaKeyword : '',
      isBlocked: (typeof isBlocked !== 'undefined') ? isBlocked : false,
      blockKeyword: (typeof blockKeyword !== 'undefined') ? blockKeyword : '',
      isLoading: (typeof isLoadingPage !== 'undefined') ? isLoadingPage : false,
      loadingKeyword: (typeof loadingKeyword !== 'undefined') ? loadingKeyword : '',
      confirmKeyword: (typeof matchedConfirm !== 'undefined') ? matchedConfirm : '',
      shadowHosts: (typeof shadowHosts !== 'undefined') ? shadowHosts : 0,
      iframeCount: (typeof iframeCount !== 'undefined') ? iframeCount : 0,
      visibleButtons: (typeof visibleBtnCount !== 'undefined') ? visibleBtnCount : buttons.length
    };

    var reportStr = JSON.stringify(report);
    var nowTs = Date.now();
    var sigChanged = (window._cpaLastReportedSig !== domSignature);
    if (sigChanged || !window._cpaLastReportedAt || (nowTs - window._cpaLastReportedAt > 1800)) {
      window._cpaLastReportedSig = domSignature;
      window._cpaLastReportedAt = nowTs;
      if (window.AndroidBridge && typeof window.AndroidBridge.onPageAnalyzed === 'function') {
        window.AndroidBridge.onPageAnalyzed(reportStr);
      }
    }
    return reportStr;
  } catch(err) {
    return JSON.stringify({ error: err.message });
  }
  }
  window._cpaRunPageAnalyzer = executeAnalyzer;
  return executeAnalyzer();
})();
        """.trimIndent()
    }

    /**
     * Autonomous Template Generator: Scans active page DOM and constructs a complete,
     * reusable WorkTemplateEntity JSON model matching the exact buttons, forms, and questions on the page.
     */
    fun buildAutoTemplateGeneratorScript(): String {
        return """
(function() {
  try {
    var title = document.title || 'Landing Page';
    var url = window.location.href;
    var steps = [];
    var stepIndex = 1;

    // 1. Initial human scroll
    steps.push({
      id: 'step_' + (stepIndex++),
      type: 'SCROLL',
      targetType: 'auto',
      scrollDirection: 'down',
      scrollAmount: 320,
      delayMs: 800,
      description: 'تمرير طبيعي للمتصفح لتحميل واستكشاف عناصر الصفحة'
    });

    // 2. Scan for Priority Offer Click or Banner buttons
    var offerCandidates = Array.from(document.querySelectorAll('a[class*="offer"], a[class*="btn"], button[class*="cta"], [role=button]'));
    if (offerCandidates.length > 0 && offerCandidates.length <= 4) {
      var firstOffer = offerCandidates[0];
      var txt = (firstOffer.innerText || firstOffer.textContent || 'Click Offer').trim();
      steps.push({
        id: 'step_' + (stepIndex++),
        type: 'CLICK_BUTTON',
        targetType: 'text',
        targetValue: txt.slice(0, 30),
        delayMs: 1200,
        description: 'نقر رابط العرض الرئيسي: ' + txt.slice(0, 25)
      });
    }

    // 3. Scan inputs for Form Fields
    var inputs = Array.from(document.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=checkbox]):not([type=radio]), select, textarea'));
    inputs.forEach(function(inp) {
      var name = ((inp.name || '') + ' ' + (inp.id || '') + ' ' + (inp.placeholder || '')).toLowerCase();
      var type = (inp.type || 'text').toLowerCase();
      var fieldKey = 'custom';
      var fieldNameAr = 'حقل نصي';

      if (type === 'email' || name.match(/email|e-mail/)) {
        fieldKey = 'email';
        fieldNameAr = 'البريد الإلكتروني من شاشة المعلومات';
      } else if (name.match(/first.?name|fname/)) {
        fieldKey = 'firstName';
        fieldNameAr = 'الاسم الأول';
      } else if (name.match(/last.?name|lname/)) {
        fieldKey = 'lastName';
        fieldNameAr = 'اسم العائلة';
      } else if (name.match(/full.?name|name/)) {
        fieldKey = 'fullName';
        fieldNameAr = 'الاسم الكامل';
      } else if (type === 'tel' || name.match(/phone|tel|mobile/)) {
        fieldKey = 'phone';
        fieldNameAr = 'رقم الهاتف المولد';
      } else if (name.match(/zip|postal|postcode/)) {
        fieldKey = 'postalCode';
        fieldNameAr = 'الرمز البريدي';
      } else if (name.match(/address|street/)) {
        fieldKey = 'address';
        fieldNameAr = 'العنوان والشارع';
      } else if (name.match(/city/)) {
        fieldKey = 'city';
        fieldNameAr = 'المدينة';
      } else if (name.match(/state/)) {
        fieldKey = 'state';
        fieldNameAr = 'الولاية / المقاطعة';
      }

      steps.push({
        id: 'step_' + (stepIndex++),
        type: 'FILL_FIELD',
        targetType: 'auto',
        fieldSource: 'identity',
        fieldKey: fieldKey,
        targetValue: inp.id || inp.name || '',
        delayMs: 800,
        description: 'ملء ' + fieldNameAr + ' في النموذج'
      });
    });

    // 4. Scan for Questions / Radios
    var radios = document.querySelectorAll('input[type=radio]');
    if (radios.length >= 2) {
      steps.push({
        id: 'step_' + (stepIndex++),
        type: 'EXTRACT_ADAPT',
        targetType: 'auto',
        delayMs: 1000,
        description: 'استخراج ذكي لأسئلة الاستبيان واختيار الإجابات المؤهلة للتحويل'
      });
    }

    // 5. Scan Checkboxes (Terms, 18+ age)
    var checkboxes = document.querySelectorAll('input[type=checkbox]');
    if (checkboxes.length > 0) {
      steps.push({
        id: 'step_' + (stepIndex++),
        type: 'CHECK_BOX',
        targetType: 'auto',
        targetValue: 'terms',
        delayMs: 600,
        description: 'تحديد مربعات الموافقة على الشروط وتأكيد السن'
      });
    }

    // 6. Scan for Skip / Upsell bypass buttons
    var allButtons = Array.from(document.querySelectorAll('button, a, [role=button], input[type=submit]'));
    var hasSkip = allButtons.some(function(b) {
      var t = (b.innerText || b.textContent || b.value || '').toLowerCase();
      return t.match(/no thanks|skip|not interested/);
    });
    if (hasSkip) {
      steps.push({
        id: 'step_' + (stepIndex++),
        type: 'CLICK_BUTTON',
        targetType: 'text',
        targetValue: 'No Thanks, Skip',
        delayMs: 900,
        description: 'تخطي العروض الدعائية والرعاة تلقائياً'
      });
    }

    // 7. Action / Submit button
    steps.push({
      id: 'step_' + (stepIndex++),
      type: 'CLICK_BUTTON',
      targetType: 'auto',
      targetValue: 'Continue, Submit, Next, Claim',
      delayMs: 1200,
      description: 'نقر زر المتابعة أو إرسال الاستمارة'
    });

    var template = {
      name: 'قالب تلقائي: ' + (title.length > 25 ? title.slice(0, 25) + '...' : title),
      description: 'تم إنشاؤه تلقائياً بالذكاء الاصطناعي من تحليل صفحة: ' + url.slice(0, 40),
      targetCategory: inputs.length > 2 ? 'Lead Gen' : (inputs.length === 1 ? 'Email Submit' : 'Survey / Quiz'),
      isAutoGenerated: true,
      steps: steps
    };

    var templateJson = JSON.stringify(template);
    if (window.AndroidBridge && typeof window.AndroidBridge.onTemplateGenerated === 'function') {
      window.AndroidBridge.onTemplateGenerated(templateJson);
    }
    return templateJson;
  } catch(e) {
    return JSON.stringify({ error: e.message });
  }
})();
        """.trimIndent()
    }

    /**
     * Executes a user-defined or auto-generated Work Template on the active page DOM.
     */
    fun buildWorkTemplateExecutionScript(stepsJson: String, identity: GeneratedIdentity): String {
        val safeSteps = stepsJson.ifBlank { "[]" }
        val identityJson = JSONObject().apply {
            put("firstName", identity.firstName)
            put("lastName", identity.lastName)
            put("fullName", identity.fullName)
            put("email", identity.email)
            put("phone", identity.phone)
            put("address", identity.address)
            put("city", identity.city)
            put("state", identity.state)
            put("postalCode", identity.postalCode)
            put("country", identity.country)
            put("birthDate", identity.birthDate)
            put("gender", identity.gender)
            put("cardNumber", identity.cardNumber)
            put("cardExpiry", identity.cardExpiry)
            put("cardCvv", identity.cardCvv)
        }.toString()

        return """
(function() {
  try {
    var steps = $safeSteps;
    var ident = $identityJson;
    if (!Array.isArray(steps) || steps.length === 0) return 'No steps';

    function setVal(el, val) {
      if (!el || val === undefined || val === null) return;
      try {
        var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value')?.set;
        if (setter) setter.call(el, val); else el.value = val;
        el.dispatchEvent(new Event('input', { bubbles: true }));
        el.dispatchEvent(new Event('change', { bubbles: true }));
        el.dispatchEvent(new Event('blur', { bubbles: true }));
      } catch(e) {
        el.value = val;
      }
    }

    function triggerElClick(el) {
      if (!el) return;
      el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      var opts = { bubbles: true, cancelable: true, view: window };
      el.dispatchEvent(new MouseEvent('mouseover', opts));
      el.dispatchEvent(new MouseEvent('mousedown', opts));
      el.focus();
      el.dispatchEvent(new MouseEvent('mouseup', opts));
      el.dispatchEvent(new MouseEvent('click', opts));
      if (typeof el.click === 'function') el.click();
    }

    var currentStep = 0;
    function executeNextStep() {
      if (currentStep >= steps.length) {
        return;
      }
      var s = steps[currentStep];
      var idx = currentStep;
      currentStep++;

      try {
        if (s.type === 'SCROLL') {
          var amt = s.scrollAmount || 300;
          if (s.scrollDirection === 'up') amt = -amt;
          window.scrollBy({ top: amt, behavior: 'smooth' });
        } else if (s.type === 'FILL_FIELD') {
          var val = ident[s.fieldKey] || s.customValue || ident.email;
          var inps = Array.from(document.querySelectorAll('input:not([type=hidden]):not([type=submit]):not([type=button]):not([type=checkbox]):not([type=radio]), select, textarea'));
          var targetInp = inps.find(function(i) {
            var n = ((i.name || '') + ' ' + (i.id || '') + ' ' + (i.placeholder || '')).toLowerCase();
            return n.includes(s.fieldKey.toLowerCase());
          }) || inps[0];
          if (targetInp) setVal(targetInp, val);
        } else if (s.type === 'CHECK_BOX') {
          var cbs = Array.from(document.querySelectorAll('input[type=checkbox]'));
          cbs.forEach(function(cb) {
            if (!cb.checked) {
              cb.checked = true;
              cb.dispatchEvent(new Event('change', { bubbles: true }));
              cb.dispatchEvent(new Event('click', { bubbles: true }));
            }
          });
        } else if (s.type === 'CLICK_BUTTON') {
          var btns = Array.from(document.querySelectorAll('button, a, [role=button], input[type=submit]'));
          var matchKw = (s.targetValue || 'continue, submit, next, claim').toLowerCase().split(',').map(function(k) { return k.trim(); });
          var btnToClick = btns.find(function(b) {
            var txt = ((b.innerText || b.textContent || b.value || '')).toLowerCase();
            return matchKw.some(function(kw) { return txt.includes(kw); });
          }) || btns[0];
          if (btnToClick) triggerElClick(btnToClick);
        } else if (s.type === 'EXTRACT_ADAPT') {
          var radios = Array.from(document.querySelectorAll('input[type=radio]'));
          if (radios.length > 0 && !radios.some(function(r) { return r.checked; })) {
            radios[0].checked = true;
            radios[0].dispatchEvent(new Event('change', { bubbles: true }));
          }
        }

        if (window.AndroidBridge && typeof window.AndroidBridge.onPageActionStepExecuted === 'function') {
          window.AndroidBridge.onPageActionStepExecuted(idx, s.description || s.type, 'done');
        }
      } catch(err) {
      }

      setTimeout(executeNextStep, s.delayMs || 1000);
    }

    executeNextStep();
    return 'Started';
  } catch(e) {
    return 'Error: ' + e.message;
  }
})();
        """.trimIndent()
    }

    /**
     * Extracts Locker URL and ID from arbitrary user input (full script tag, direct URL, or numeric ID).
     */
    fun extractLockerUrlAndId(input: String): Pair<String, String> {
        val clean = input.trim()
        val tagRegex = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val tagMatch = tagRegex.find(clean)
        val extractedUrl = tagMatch?.groupValues?.get(1) ?: if (clean.startsWith("http://") || clean.startsWith("https://")) clean else ""

        val idRegex = Regex("""id=([0-9a-zA-Z_-]+)""", RegexOption.IGNORE_CASE)
        val idFromUrl = if (extractedUrl.isNotBlank()) idRegex.find(extractedUrl)?.groupValues?.get(1) else null
        val finalId = idFromUrl ?: if (clean.isNotBlank() && clean.all { it.isDigit() || it == '-' || it == '_' }) clean else "1741238"

        val finalUrl = if (extractedUrl.isNotBlank()) extractedUrl else "https://alignmentfiles.com/script_include.php?id=$finalId"
        return Pair(finalUrl, finalId)
    }

    /**
     * Advanced CPA Content Locker Detector, Polyfiller & Force-Trigger Engine.
     * Works with CPAGrip, AlignmentFiles, FileTrkr, and custom offer lock scripts.
     *
     * Features:
     * - Polyfills document.write to unblock dynamically written scripts in modern WebView
     * - Injects jQuery safely if missing from the host website
     * - Detects existing <script> tags or embedded script_include.php references
     * - Dispatches call_locker(), DisplayInlineBox(), load_slidepage()
     * - Fixes iframe & modal container visibility, zIndex and dimensions
     * - Injects custom script URL or ID if not present on the webpage
     */
    fun buildCpaLockerDetectorAndActivatorScript(
        customUrl: String? = null,
        customId: String? = null,
        forceTrigger: Boolean = true,
        forceInjectIfMissing: Boolean = false
    ): String {
        val safeCustomUrl = (customUrl ?: "").trim().replace("'", "\\'")
        val safeCustomId = (customId ?: "1741238").trim().replace("'", "\\'")

        return """
(function() {
  try {
    function emitHumanStatus(msg) {
      try {
        if (window.AndroidBridge && typeof window.AndroidBridge.onHumanActionStatus === 'function') {
          window.AndroidBridge.onHumanActionStatus(msg);
        }
      } catch(e) {}
    }

    // Human Bezier pointer & touch click helper for landing-page locker trigger buttons
    function humanClickTriggerElement(el, onDone) {
      if (!el) { if (onDone) onDone(); return; }
      try {
        if (el.scrollIntoView) {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      } catch(e) {}

      setTimeout(function() {
        try {
          var rect = el.getBoundingClientRect ? el.getBoundingClientRect() : { left: 160, top: 280, width: 120, height: 44 };
          var endX = Math.round(rect.left + (rect.width || 100) * (0.35 + Math.random() * 0.3));
          var endY = Math.round(rect.top + (rect.height || 40) * (0.35 + Math.random() * 0.3));
          var startX = Math.round((window.innerWidth || 380) * (0.2 + Math.random() * 0.6));
          var startY = Math.round((window.innerHeight || 680) * (0.25 + Math.random() * 0.5));
          var ctrlX = Math.round((startX + endX) / 2 + (Math.random() - 0.5) * 90);
          var ctrlY = Math.round((startY + endY) / 2 + (Math.random() - 0.5) * 70);

          var steps = 8;
          for (var s = 1; s <= steps; s++) {
            (function(stepIdx) {
              setTimeout(function() {
                try {
                  var t = stepIdx / steps;
                  var inv = 1 - t;
                  var cx = Math.round(inv * inv * startX + 2 * inv * t * ctrlX + t * t * endX);
                  var cy = Math.round(inv * inv * startY + 2 * inv * t * ctrlY + t * t * endY);
                  document.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientX: cx, clientY: cy, pointerType: 'touch' }));
                  document.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: cx, clientY: cy }));
                } catch(e) {}
              }, stepIdx * 28);
            })(s);
          }

          setTimeout(function() {
            try {
              var evOpts = { bubbles: true, cancelable: true, view: window, clientX: endX, clientY: endY };
              el.dispatchEvent(new PointerEvent('pointerover', evOpts));
              el.dispatchEvent(new MouseEvent('mouseover', evOpts));
              el.dispatchEvent(new PointerEvent('pointerdown', evOpts));
              el.dispatchEvent(new MouseEvent('mousedown', evOpts));
              if (typeof el.focus === 'function') el.focus();
            } catch(e) {}

            var holdMs = 75 + Math.floor(Math.random() * 65);
            setTimeout(function() {
              try {
                var upOpts = { bubbles: true, cancelable: true, view: window, clientX: endX, clientY: endY };
                el.dispatchEvent(new PointerEvent('pointerup', upOpts));
                el.dispatchEvent(new MouseEvent('mouseup', upOpts));
                el.dispatchEvent(new MouseEvent('click', upOpts));
                if (typeof el.click === 'function') el.click();
              } catch(e) {}
              if (onDone) onDone();
            }, holdMs);
          }, steps * 28 + 140);
        } catch(err) {
          try { if (el.click) el.click(); } catch(e) {}
          if (onDone) onDone();
        }
      }, 320);
    }

    // 1. Detect Locker Script / Iframe / Inline Markup across all major networks
    var detectedUrl = '';
    var detectedId = '';
    var lockerNetworkPatterns = [
      'script_include.php', 'alignmentfiles', 'cpagrip', 'filetrkr', 'ogads',
      'cpabuild', 'adbluemedia', 'cpalead', 'adworkmedia', 'lockr', 'content_locker'
    ];
    var scriptTags = Array.from(document.querySelectorAll('script[src]'));
    for (var i = 0; i < scriptTags.length; i++) {
      var src = scriptTags[i].src || '';
      var srcLow = src.toLowerCase();
      if (lockerNetworkPatterns.some(function(p) { return srcLow.indexOf(p) !== -1; })) {
        detectedUrl = src;
        var m = src.match(/[?&]id=([0-9a-zA-Z_-]+)/i) || src.match(/\/([0-9]{4,10})(?:\.js|$)/i);
        if (m && m[1]) detectedId = m[1];
        break;
      }
    }

    // Also scan raw HTML markup for embedded locker URLs or locker variable definitions
    if (!detectedUrl && document.documentElement) {
      var html = document.documentElement.innerHTML || '';
      var rawMatch = html.match(/(https?:\/\/[^\s"'<>]*(?:alignmentfiles\.com|cpagrip\.com|filetrkr\.com|ogads\.com|cpabuild\.com|adbluemedia\.com|cpalead\.com)[^\s"'<>]*script_include\.php\?id=([0-9a-zA-Z_-]+))/i);
      if (rawMatch) {
        detectedUrl = rawMatch[1];
        detectedId = rawMatch[2];
      } else {
        var itMatch = html.match(/it\s*:\s*([0-9]{4,10})\s*,\s*key\s*:\s*['"]([0-9a-zA-Z]+)['"]/i);
        if (itMatch) {
          detectedUrl = 'cpabuild_adbluemedia_it_' + itMatch[1];
          detectedId = itMatch[1];
        }
      }
    }

    // Report initial detection to Android
    if (detectedUrl && window.AndroidBridge && typeof window.AndroidBridge.onLockerDetected === 'function') {
      window.AndroidBridge.onLockerDetected(detectedUrl, detectedId || '1741238', false);
    }

    var targetUrl = detectedUrl;
    var targetId = detectedId;

    if (!targetUrl && $forceInjectIfMissing) {
      targetId = '$safeCustomId'.trim() || '1741238';
      targetUrl = '$safeCustomUrl'.trim() || ('https://alignmentfiles.com/script_include.php?id=' + targetId);
    }

    if (targetUrl && targetUrl.indexOf('http') === 0) {
      var existingTag = document.querySelector('script[src*="' + (targetId || 'script_include') + '"]');
      if (!existingTag) {
        var tag = document.createElement('script');
        tag.type = 'text/javascript';
        tag.src = targetUrl;
        tag.async = false;
        (document.head || document.documentElement || document.body).appendChild(tag);
        if (window.AndroidBridge && typeof window.AndroidBridge.onLockerStatus === 'function') {
          window.AndroidBridge.onLockerStatus('تم تفعيل سكريبت اللوكر: #' + targetId);
        }
      }
    }

    function isLockerModalVisibleNow() {
      var sel = '#InlineBoxMainOuterLayer, #InlineBoxDiv, #the_box, div[id*="InlineBox"], iframe[src*="alignmentfiles"], iframe[src*="load_box"], iframe[src*="cpagrip"], iframe[src*="ogads"], iframe[src*="cpabuild"], iframe[src*="adbluemedia"], iframe[src*="filetrkr"], iframe[src*="locker"], div[class*="cpa_locker"], div[id*="cpa_locker"], div[class*="locker-offers"], div[id*="locker-offers"], .offer_link';
      var els = document.querySelectorAll(sel);
      for (var k = 0; k < els.length; k++) {
        var r = els[k].getBoundingClientRect ? els[k].getBoundingClientRect() : null;
        var st = window.getComputedStyle ? window.getComputedStyle(els[k]) : null;
        if (r && r.width > 10 && r.height > 10 && (!st || (st.display !== 'none' && st.visibility !== 'hidden' && st.opacity !== '0'))) {
          return true;
        }
      }
      return false;
    }

    function unhideAndElevateLockerElements() {
      var lockerSelectors = [
        '#InlineBoxMainOuterLayer', '#InlineBoxDiv', '#the_box', 'div[id*="InlineBox"]',
        'iframe[src*="alignmentfiles"]', 'iframe[src*="load_box"]', 'iframe[src*="cpagrip"]',
        'iframe[src*="ogads"]', 'iframe[src*="cpabuild"]', 'iframe[src*="adbluemedia"]',
        'iframe[src*="filetrkr"]', 'iframe[src*="locker"]', 'div[class*="cpa_locker"]',
        'div[id*="cpa_locker"]', 'div[class*="locker-offers"]', 'div[id*="locker-offers"]'
      ];
      var foundAny = false;
      lockerSelectors.forEach(function(sel) {
        document.querySelectorAll(sel).forEach(function(el) {
          foundAny = true;
          try {
            el.style.display = 'block';
            el.style.visibility = 'visible';
            el.style.opacity = '1';
            el.style.pointerEvents = 'auto';
            el.style.zIndex = '2147483647';
          } catch(e) {}
        });
      });
      return foundAny;
    }

    function findLandingPageLockerTriggerButton() {
      // 1. Explicit locker onclick / href attributes
      var explicitSels = [
        '[onclick*="call_locker"]', '[onclick*="DisplayInlineBox"]', '[onclick*="og_load"]',
        '[onclick*="CPABuildLock"]', '[onclick*="_JF"]', '[onclick*="_Wi"]', '[onclick*="locker"]',
        'a[href*="call_locker"]', 'a[href*="javascript:call_locker"]', 'button[id*="locker" i]',
        'button[class*="locker" i]', 'a[class*="locker" i]', 'div[class*="locker-btn" i]'
      ];
      var explicitBtns = document.querySelectorAll(explicitSels.join(','));
      for (var i = 0; i < explicitBtns.length; i++) {
        if (explicitBtns[i].offsetParent !== null || explicitBtns[i].offsetWidth > 0) return explicitBtns[i];
      }

      // 2. If a locker script or global locker function is present on this landing page, look for the primary "Unlock / Verify / Claim / Generate" button
      var hasLockerScriptOrFn = !!detectedUrl ||
        typeof window.call_locker === 'function' ||
        typeof window.DisplayInlineBox === 'function' ||
        typeof window.og_load === 'function' ||
        typeof window.CPABuildLock === 'function';

      if (hasLockerScriptOrFn) {
        var triggerWords = [
          'unlock', 'verify', 'human verification', 'claim now', 'claim reward', 'claim your',
          'get code', 'get reward', 'generate', 'download now', 'download', 'continue',
          'start verification', 'complete verification', 'get gift card', 'open locker',
          'فك القفل', 'تحقق', 'احصل على', 'متابعة', 'تأكيد'
        ];
        var ctas = document.querySelectorAll('button, a.btn, a.button, [role="button"], div.btn, div.button, a[href="#"], a[href^="javascript:"]');
        for (var c = 0; c < ctas.length; c++) {
          var btn = ctas[c];
          if (btn.offsetParent === null && btn.offsetWidth === 0) continue;
          var txt = ((btn.innerText || btn.textContent || btn.getAttribute('aria-label') || '') + '').toLowerCase().trim();
          if (txt.length >= 3 && txt.length <= 48 && triggerWords.some(function(w) { return txt.indexOf(w) !== -1; })) {
            return btn;
          }
        }
      }
      return null;
    }

    function invokeLockerGlobalFunctions() {
      var triggered = false;
      var fnNames = [
        'call_locker', 'DisplayInlineBox', 'call1', 'call2', 'load_slidepage',
        'og_load', 'CPABuildLock', '_JF', '_Wi', 'show_locker', 'open_locker',
        'load_locker', 'start_locker', 'cpa_complete'
      ];
      for (var f = 0; f < fnNames.length; f++) {
        var fn = fnNames[f];
        if (typeof window[fn] === 'function') {
          try { window[fn](); triggered = true; break; } catch(e) {}
        }
      }
      return triggered;
    }

    if ($forceTrigger && !window.__cpa_locker_activation_running) {
      window.__cpa_locker_activation_running = true;

      // Step 1: Natural human reading & gentle scroll on the landing page before touching the locker (850ms - 1450ms)
      var hasAnyLockerHint = !!detectedUrl || !!targetUrl || isLockerModalVisibleNow() || !!findLandingPageLockerTriggerButton();
      if (hasAnyLockerHint) {
        emitHumanStatus('👁️ مسح بصري بشري لصفحة الهبوط وفحص بوابة اللوكر...');
        try {
          window.scrollBy({ top: Math.floor(90 + Math.random() * 110), behavior: 'smooth' });
        } catch(e) {}
      }

      var initialHumanPauseMs = 750 + Math.floor(Math.random() * 550);
      setTimeout(function() {
        // If locker modal is already visible on page load, just unhide & report
        if (isLockerModalVisibleNow()) {
          unhideAndElevateLockerElements();
          window.__cpa_locker_detected = true;
          emitHumanStatus('🔓 نافذة اللوكر ظاهرة وجاهزة — جاري فحص العروض بشرياً...');
          if (window.AndroidBridge && typeof window.AndroidBridge.onLockerDetected === 'function') {
            window.AndroidBridge.onLockerDetected(detectedUrl || targetUrl || 'detected_locker', detectedId || targetId || '1741238', true);
          }
          window.__cpa_locker_activation_running = false;
          return;
        }

        // Step 2: Try clicking the actual human CTA / Unlock button on the landing page first!
        var landingTriggerBtn = findLandingPageLockerTriggerButton();
        if (landingTriggerBtn && !window.__cpa_landing_trigger_clicked) {
          window.__cpa_landing_trigger_clicked = true;
          var btnTitle = ((landingTriggerBtn.innerText || landingTriggerBtn.textContent || 'Unlock') + '').trim().slice(0, 24);
          emitHumanStatus('👆 تحريك المؤشر البشري والنقر على زر فتح اللوكر: «' + btnTitle + '»...');
          humanClickTriggerElement(landingTriggerBtn, function() {
            // After human click on the button, wait 900ms to see if the locker modal opened naturally
            setTimeout(function() {
              if (!isLockerModalVisibleNow()) {
                invokeLockerGlobalFunctions();
              }
              unhideAndElevateLockerElements();
            }, 900);
          });
        }

        // Step 3: Poll gracefully for up to 10 seconds to ensure locker opens and stays visible
        var triggerAttempts = 0;
        var triggerTimer = setInterval(function() {
          triggerAttempts++;
          var visibleNow = isLockerModalVisibleNow();
          var triggered = visibleNow;

          if (!visibleNow && triggerAttempts >= 2) {
            if (invokeLockerGlobalFunctions()) {
              triggered = true;
            }
          }

          if (unhideAndElevateLockerElements()) {
            triggered = true;
            window.__cpa_locker_detected = true;
          }

          if (!triggered && triggerAttempts < 18) return;
          clearInterval(triggerTimer);
          window.__cpa_locker_activation_running = false;

          if (window.AndroidBridge && (detectedUrl || targetUrl || triggered)) {
            if (typeof window.AndroidBridge.onLockerDetected === 'function') {
              window.AndroidBridge.onLockerDetected(detectedUrl || targetUrl || 'detected_locker', detectedId || targetId || '1741238', triggered);
            }
            if (typeof window.AndroidBridge.onLockerStatus === 'function') {
              window.AndroidBridge.onLockerStatus(triggered ? '🔓 تم فتح وتفعيل اللوكر بشرياً بنجاح!' : 'تم تجهيز سكريبت اللوكر');
            }
          }
        }, 550);
      }, initialHumanPauseMs);
    }

    return 'Smart Human Locker Engine Ready';
  } catch(e) {
    return 'Locker Engine Error: ' + e.message;
  }
})();
true;
        """.trimIndent()
    }

    /**
     * Autonomous Content Locker Offer Scanner, Poller and Auto-Clicker.
     * Handles CPA Grip, AlignmentFiles, OGAds, AdWorkMedia, and custom locker overlays / iframes.
     * When categories 'offer click' and 'locker' are combined, this script:
     * 1. Continuously waits and watches for the Locker to appear (DOM + iframes + MutationObserver).
     * 2. Matches available offers against keywords from the Offer Click screen.
     * 3. Programmatically clicks the matching offer.
     * 4. Dispatches the destination URL to AndroidBridge to seamlessly open and transition work to a new tab.
     */
    fun buildLockerOfferAutoClickScript(
        clickTexts: List<String> = emptyList(),
        activeTargetText: String? = null,
        selectionStrategy: String = "priority", // "priority", "first", "random"
        openInNewTab: Boolean = true
    ): String {
        val clickTextsArray = org.json.JSONArray().apply {
            clickTexts.filter { it.isNotBlank() }.forEach { put(it) }
        }.toString()
        val activeTargetJson = if (activeTargetText.isNullOrBlank()) "null" else org.json.JSONObject.quote(activeTargetText)
        val strategyJson = org.json.JSONObject.quote(selectionStrategy)

        return """
(function() {
  try {
    if (window.__cpa_locker_offer_clicked || window.__cpa_locker_human_sequence_busy) return 'already_clicked_or_busy';

    var targetList = [];
    var active = $activeTargetJson;
    var allTexts = $clickTextsArray;
    var strategy = $strategyJson;
    var openNewTab = $openInNewTab;

    if (active && active.trim().length > 0) targetList.push(active.trim().toLowerCase());
    for (var i = 0; i < allTexts.length; i++) {
      var s = (allTexts[i] || '').trim().toLowerCase();
      if (s && targetList.indexOf(s) === -1) targetList.push(s);
    }
    if (targetList.length === 0) {
      targetList = ['get a ${'$'}100 nike gift card', 'walmart', 'cash app', 'gift card', '${'$'}1000', '${'$'}750', '${'$'}500', 'claim', 'survey', 'reward', 'free', 'win', 'card', 'nike'];
    }

    window.__cpa_clicked_offer_keys = window.__cpa_clicked_offer_keys || {};

    function emitHumanStatus(msg) {
      try {
        if (window.AndroidBridge && typeof window.AndroidBridge.onHumanActionStatus === 'function') {
          window.AndroidBridge.onHumanActionStatus(msg);
        }
      } catch(e) {}
    }

    function cleanOfferTitle(raw) {
      if (!raw) return '';
      return ('' + raw)
        .replace(/[\r\n\t]+/g, ' ')
        .replace(/\s+/g, ' ')
        .replace(/^(?:[0-9]{1,2}[\.\)\-\:]\s*|[•▸►➔➜★☆⚡🔥🎁👉]+\s*)+/i, '')
        .trim();
    }

    function isJunkCandidate(txt, href) {
      if (!txt || txt.length < 3 || txt.length > 145) return true;
      var low = txt.toLowerCase().trim();
      var junkExact = ['close', 'cancel', 'privacy', 'privacy policy', 'terms', 'dmca', 'help', 'contact', 'skip', 'back'];
      if (junkExact.indexOf(low) !== -1) return true;
      if (low.indexOf('powered by') !== -1 || low.indexOf('complete an offer below') !== -1 || low.indexOf('human verification required') !== -1) return true;
      var hLow = (href || '').toLowerCase();
      if (hLow.indexOf('script_include.php') !== -1 || hLow.indexOf('load_box.php') !== -1) return true;
      return false;
    }

    function scoreOfferConversionEase(txt, href) {
      var low = ((txt || '') + ' ' + (href || '')).toLowerCase();
      var score = 70;
      var categoryTag = 'Locker Offer 🔒';
      var brandTag = '';

      var brands = [
        { k: 'nike', b: 'Nike', bonus: 14 },
        { k: 'walmart', b: 'Walmart', bonus: 12 },
        { k: 'cash app', b: 'Cash App', bonus: 12 },
        { k: 'cashapp', b: 'Cash App', bonus: 12 },
        { k: 'amazon', b: 'Amazon', bonus: 11 },
        { k: 'target', b: 'Target', bonus: 10 },
        { k: 'paypal', b: 'PayPal', bonus: 10 },
        { k: 'apple', b: 'Apple', bonus: 9 },
        { k: 'shein', b: 'SHEIN', bonus: 9 },
        { k: 'google play', b: 'Google Play', bonus: 8 },
        { k: 'roblox', b: 'Roblox', bonus: 8 }
      ];
      for (var i = 0; i < brands.length; i++) {
        if (low.indexOf(brands[i].k) !== -1) {
          brandTag = brands[i].b;
          score += brands[i].bonus;
          break;
        }
      }

      // SOI / Email / Zip Submit = Easiest & Fastest Web Conversions
      if (low.indexOf('email') !== -1 || low.indexOf('zip') !== -1 || low.indexOf('enter your') !== -1 || low.indexOf('soi') !== -1) {
        score += 24;
        categoryTag = 'Email / SOI 📧';
      } else if (low.indexOf('gift card') !== -1 || low.indexOf('reward') !== -1 || low.indexOf('claim') !== -1 || low.indexOf('get a ${'$'}') !== -1 || low.indexOf('get ${'$'}') !== -1 || low.indexOf('win ') !== -1) {
        score += 18;
        categoryTag = 'Gift Card / Reward 🎁';
      } else if (low.indexOf('survey') !== -1 || low.indexOf('quiz') !== -1 || low.indexOf('opinion') !== -1 || low.indexOf('question') !== -1) {
        score += 12;
        categoryTag = 'Survey / Quiz 📊';
      }

      // Penalize App Installs, Downloads, SMS PINs, or Credit Card Trials in WebView
      if (low.indexOf('install') !== -1 || low.indexOf('download') !== -1 || low.indexOf('play.google') !== -1 || low.indexOf('itunes.apple') !== -1 || low.indexOf('open the app') !== -1) {
        score -= 32;
        categoryTag = 'App Install 📲';
      }
      if (low.indexOf('pin submit') !== -1 || low.indexOf('send sms') !== -1 || low.indexOf('credit card') !== -1 || low.indexOf('free trial') !== -1) {
        score -= 38;
        categoryTag = 'PIN / Trial 📱';
      }

      return {
        score: Math.max(10, Math.min(100, score)),
        categoryTag: categoryTag,
        brandTag: brandTag
      };
    }

    function scanDocForOffers(doc, isIframe, list, seenKeys) {
      if (!doc) return;
      var selectors = [
        '#InlineBoxDiv a', '#the_box a', '#main_div a', '.offer_link', '.offer-link',
        '.locker_offer', '.locker-offer', '[class*="offer-item"] a', '[class*="offer_item"] a',
        '[class*="offer-row"] a', '[class*="offer_row"] a', 'div[class*="offer"] a',
        'li[id*="offer"] a', 'li[class*="offer"] a', 'tr[class*="offer"] a',
        'table[class*="offer"] a', '[data-offer-id] a', '[data-offer-id]',
        'a[href*="tracking"]', 'a[href*="load_box"]', 'a[href*="show_file"]',
        'a[href*="click.php"]', 'a[href*="alignmentfiles"]', 'a[href*="cpagrip"]',
        'a[href*="ogads"]', 'a[href*="cpabuild"]', 'a[href*="adbluemedia"]',
        'a[href*="filetrkr"]', 'a[href*="offer_id="]', 'a[class*="offer"]',
        'a[id*="offer"]', 'button[class*="offer"]', 'button[onclick*="offer"]'
      ];

      try {
        var found = doc.querySelectorAll(selectors.join(','));
        for (var k = 0; k < found.length; k++) {
          var el = found[k];
          if (el.offsetParent === null && el.offsetWidth === 0 && el.offsetHeight === 0) continue;
          var titleEl = el.querySelector ? el.querySelector('.offer_title, .offer-title, .title, .name, h3, h4, strong, b, span[class*="title"]') : null;
          var rawTxt = (titleEl && (titleEl.innerText || titleEl.textContent || '').trim().length >= 4)
            ? (titleEl.innerText || titleEl.textContent || '')
            : ((el.innerText || el.textContent || '') + ' ' + (el.getAttribute('title') || '') + ' ' + (el.getAttribute('aria-label') || ''));
          var txt = cleanOfferTitle(rawTxt);
          var href = el.href || el.getAttribute('href') || el.getAttribute('data-url') || el.getAttribute('data-href') || '';
          if (!href && el.getAttribute('onclick')) {
            var m = el.getAttribute('onclick').match(/https?:\/\/[^\s'"]+/);
            if (m) href = m[0];
          }
          if (isJunkCandidate(txt, href)) continue;
          var normKey = txt.toLowerCase().replace(/[^a-z0-9\u0600-\u06FF${'$'}]+/g, '');
          if (!normKey || normKey.length < 3 || seenKeys[normKey]) continue;
          seenKeys[normKey] = true;

          var meta = scoreOfferConversionEase(txt, href);
          list.push({
            element: el,
            doc: doc,
            text: txt,
            normKey: normKey,
            href: href,
            isIframe: isIframe,
            easeScore: meta.score,
            categoryTag: meta.categoryTag,
            brandTag: meta.brandTag
          });
        }
      } catch(e) {}
    }

    function collectAllCandidates() {
      var candidates = [];
      var seenKeys = {};
      scanDocForOffers(document, false, candidates, seenKeys);

      var iframes = document.querySelectorAll('iframe');
      for (var f = 0; f < iframes.length; f++) {
        try {
          var iframeDoc = iframes[f].contentDocument || (iframes[f].contentWindow ? iframes[f].contentWindow.document : null);
          if (iframeDoc) {
            scanDocForOffers(iframeDoc, true, candidates, seenKeys);
          }
        } catch(frameErr) {
          // Cross-origin locker iframe is handled by buildCrossOriginSubframeWorkerScript + postMessage
        }
      }

      // Also broadcast to any cross-origin locker iframe if activeTargetText was explicitly requested
      if (active && active.trim().length > 0) {
        try {
          for (var w = 0; w < window.frames.length; w++) {
            try {
              window.frames[w].postMessage({
                type: 'CPA_CLICK_LOCKER_OFFER',
                targetText: active.trim(),
                openInNewTab: openNewTab
              }, '*');
            } catch(e) {}
          }
        } catch(e) {}
      }

      var isLockerExpected = !!document.querySelector(
        'script[src*="script_include"], script[src*="alignmentfiles"], script[src*="cpagrip"], script[src*="filetrkr"], script[src*="ogads"], script[src*="cpabuild"], script[src*="adbluemedia"], iframe[src*="load_box"], #InlineBoxMainOuterLayer, #the_box, div[class*="cpa_locker"], div[id*="cpa_locker"]'
      );

      if (candidates.length === 0 && !isLockerExpected) {
        var generalLinks = document.querySelectorAll('a, button, [role=button], div[class*="btn"], div[class*="cta"]');
        for (var g = 0; g < generalLinks.length; g++) {
          var gel = generalLinks[g];
          if (gel.offsetParent === null && gel.offsetWidth === 0 && gel.offsetHeight === 0) continue;
          var gtxt = cleanOfferTitle(gel.innerText || gel.textContent || '');
          if (isJunkCandidate(gtxt, '')) continue;
          var gLow = gtxt.toLowerCase();
          for (var t = 0; t < targetList.length; t++) {
            if (gLow.indexOf(targetList[t]) !== -1) {
              var clickable = gel.closest('a, button, [role=button]') || gel;
              var gh = clickable.href || (clickable.getAttribute ? clickable.getAttribute('href') : null) || '';
              var meta = scoreOfferConversionEase(gtxt, gh);
              candidates.push({
                element: clickable,
                doc: document,
                text: gtxt,
                normKey: gLow.replace(/[^a-z0-9]+/g, ''),
                href: gh,
                isIframe: false,
                easeScore: meta.score,
                categoryTag: meta.categoryTag,
                brandTag: meta.brandTag
              });
              break;
            }
          }
        }
      }
      return candidates;
    }

    function normalizeOfferText(s) {
      if (!s) return '';
      return ('' + s).toLowerCase().replace(/[^a-z0-9${'$'}]+/g, ' ').replace(/\s+/g, ' ').trim();
    }

    function fuzzyOfferMatch(candidateRaw, targetRaw) {
      if (!candidateRaw || !targetRaw) return false;
      var cand = ('' + candidateRaw).toLowerCase();
      var targ = ('' + targetRaw).toLowerCase();
      if (cand.indexOf(targ) !== -1) return true;
      var cleanCand = normalizeOfferText(cand);
      var cleanTarg = normalizeOfferText(targ);
      if (cleanCand.indexOf(cleanTarg) !== -1) return true;
      var tokens = cleanTarg.split(' ').filter(function(w) { return w.length >= 3 || /\d/.test(w); });
      var strong = tokens.filter(function(w) { return ['get','and','the','for','you','your','with','card','gift','nike','100','reward','claim','free','win'].indexOf(w) !== -1 ? true : w.length >= 4; });
      if (strong.length >= 2) {
        var hits = 0;
        for (var i = 0; i < strong.length; i++) { if (cleanCand.indexOf(strong[i]) !== -1) hits++; }
        var brandTokens = ['nike','walmart','amazon','cash','target','apple','paypal','shein','roblox'];
        var brandInTarget = null;
        for (var b = 0; b < brandTokens.length; b++) { if (cleanTarg.indexOf(brandTokens[b]) !== -1) { brandInTarget = brandTokens[b]; break; } }
        if (brandInTarget && cleanCand.indexOf(brandInTarget) === -1) return false;
        if (hits >= Math.min(strong.length, 3)) return true;
        if (hits >= 2 && cleanCand.indexOf('gift') !== -1) return true;
      } else if (tokens.length >= 1) {
        var allIn = true;
        for (var j = 0; j < tokens.length; j++) { if (cleanCand.indexOf(tokens[j]) === -1) { allIn = false; break; } }
        if (allIn) return true;
      }
      return false;
    }

    function resolveOfferUrl(rawHref, el) {
      try {
        var h = (rawHref || '').trim();
        if (!h && el) {
          var anchor = (el.closest) ? el.closest('a') : null;
          if (anchor) h = anchor.href || anchor.getAttribute('href') || anchor.getAttribute('data-url') || '';
          if (!h && el.getAttribute) h = el.getAttribute('data-url') || el.getAttribute('data-href') || '';
          if (!h && el.getAttribute && el.getAttribute('onclick')) {
            var m = el.getAttribute('onclick').match(/https?:\/\/[^\s'"]+/);
            if (m) h = m[0];
          }
        }
        if (!h) return '';
        h = h.trim();
        if (h.indexOf('javascript:') === 0 || h === '#' || h === '') return '';
        if (h.indexOf('http') !== 0) {
          try { h = new URL(h, document.baseURI || window.location.href).toString(); } catch(e) { return ''; }
        }
        if (h.indexOf('http') !== 0) return '';
        if (h.indexOf('script_include.php') !== -1 || h.indexOf('load_box.php') !== -1) return '';
        if (h === window.location.href) return '';
        return h;
      } catch(e) { return ''; }
    }

    function dismissLandingOverlays() {
      try {
        var sels = [
          '#cookieChoiceDismiss', '.cookie-choices-button', '[aria-label*="cookie" i]',
          '.cookie-notice button', '#cookie-notice button', '.consent button',
          'button[id*="cookie" i]', 'button[class*="cookie" i]', 'a[class*="cookie" i]'
        ];
        var texts = ['got it', 'ok', 'accept', 'agree', 'allow', 'understand', 'dismiss', 'close'];
        for (var s = 0; s < sels.length; s++) {
          var els = document.querySelectorAll(sels[s]);
          for (var k = 0; k < els.length; k++) {
            try {
              var t = ((els[k].innerText || els[k].textContent || '') + '').toLowerCase().trim();
              if (t.length < 40 && (t === '' || texts.some(function(w){ return t.indexOf(w) !== -1; }))) {
                if (els[k].offsetParent !== null) els[k].click();
              }
            } catch(e) {}
          }
        }
      } catch(e) {}
    }

    function reportOffersForLearning(cands, matched) {
      try {
        var names = cands.slice(0, 8).map(function(o) { return (o.text || '(no-text)').slice(0, 42); }).join(' | ');
        if (window.AndroidBridge && typeof window.AndroidBridge.onMidPageInfoExtracted === 'function') {
          window.AndroidBridge.onMidPageInfoExtracted(matched ? 'locker_offers_matched' : 'locker_offers_available', names);
        }
        if (window.AndroidBridge && typeof window.AndroidBridge.onLockerOffersExtracted === 'function' && cands.length > 0) {
          var autoOffers = cands.map(function(c) {
            return {
              text: c.text,
              href: resolveOfferUrl(c.href, c.element),
              categoryTag: c.categoryTag || 'Locker Offer 🔒',
              brandTag: c.brandTag || '',
              priorityScore: c.easeScore || 80
            };
          });
          window.AndroidBridge.onLockerOffersExtracted(JSON.stringify({
            lockerId: 'auto_locker',
            lockerUrl: window.location.href || '',
            isLockerDetected: true,
            isManualTrigger: false,
            offers: autoOffers
          }));
        }
      } catch(e) {}
    }

    // Simulates a realistic human comparing offers in the locker, hovering, moving along a Bezier trajectory,
    // pressing the chosen offer so the locker's internal lead-polling starts, and opening the offer tab!
    function executeHumanLockerOfferSequence(chosen, allCandidates) {
      if (!chosen || window.__cpa_locker_offer_clicked || window.__cpa_locker_human_sequence_busy) return false;
      window.__cpa_locker_human_sequence_busy = true;
      dismissLandingOverlays();

      var offerTitle = (chosen.text || 'Locker Offer').trim();
      var shortTitle = offerTitle.length > 34 ? offerTitle.substring(0, 34) + '...' : offerTitle;
      var offerUrl = resolveOfferUrl(chosen.href, chosen.element);

      // Stage 1: Human Reading & Visual Comparison of Locker Offers (750ms - 1350ms)
      emitHumanStatus('🧐 قراءة قائمة عروض اللوكر (' + allCandidates.length + ' عروض) ومقارنتها بشرياً...');
      if (window.AndroidBridge && typeof window.AndroidBridge.onLockerStatus === 'function') {
        try { window.AndroidBridge.onLockerStatus('🧐 فحص ومقارنة ' + allCandidates.length + ' عروض باللوكر لاختيار الأسهل تحويلاً...'); } catch(e) {}
      }

      // Hover briefly over an alternative offer if multiple exist (authentic human comparison behavior)
      var compareDelay = 650 + Math.floor(Math.random() * 500);
      if (allCandidates.length > 1) {
        var altOffer = allCandidates.find(function(c) { return c !== chosen; }) || allCandidates[0];
        if (altOffer && altOffer.element) {
          try {
            var altRect = altOffer.element.getBoundingClientRect();
            var ax = Math.round(altRect.left + altRect.width * 0.45);
            var ay = Math.round(altRect.top + altRect.height * 0.5);
            altOffer.element.dispatchEvent(new PointerEvent('pointerover', { bubbles: true, clientX: ax, clientY: ay }));
            altOffer.element.dispatchEvent(new MouseEvent('mouseover', { bubbles: true, clientX: ax, clientY: ay }));
            altOffer.element.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: ax, clientY: ay }));
          } catch(e) {}
        }
      }

      // Stage 2: Move Pointer via Cubic Bezier Curve to the Chosen Best Offer (after compareDelay)
      setTimeout(function() {
        emitHumanStatus('🎯 التصويب البشري نحو العرض الأفضل: «' + shortTitle + '»...');
        try {
          if (chosen.element && chosen.element.scrollIntoView) {
            chosen.element.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        } catch(e) {}

        var rect = (chosen.element && chosen.element.getBoundingClientRect)
          ? chosen.element.getBoundingClientRect()
          : { left: 160, top: 300, width: 180, height: 42 };
        var targetX = Math.round(rect.left + Math.max(24, (rect.width || 160) * (0.30 + Math.random() * 0.40)));
        var targetY = Math.round(rect.top + Math.max(12, (rect.height || 38) * (0.30 + Math.random() * 0.40)));
        var startX = Math.round((window.innerWidth || 380) * (0.25 + Math.random() * 0.5));
        var startY = Math.round((window.innerHeight || 680) * (0.25 + Math.random() * 0.5));
        var ctrlX = Math.round((startX + targetX) / 2 + (Math.random() - 0.5) * 85);
        var ctrlY = Math.round((startY + targetY) / 2 + (Math.random() - 0.5) * 65);

        var steps = 10;
        for (var s = 1; s <= steps; s++) {
          (function(idx) {
            setTimeout(function() {
              try {
                var t = idx / steps;
                var inv = 1 - t;
                var cx = Math.round(inv * inv * startX + 2 * inv * t * ctrlX + t * t * targetX);
                var cy = Math.round(inv * inv * startY + 2 * inv * t * ctrlY + t * t * targetY);
                document.dispatchEvent(new PointerEvent('pointermove', { bubbles: true, clientX: cx, clientY: cy, pointerType: 'touch' }));
                document.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: cx, clientY: cy }));
              } catch(e) {}
            }, idx * 32);
          })(s);
        }

        // Stage 3: Human Hover -> Press -> Release -> Activate Locker Lead-Polling -> Open Offer Tab
        setTimeout(function() {
          window.__cpa_locker_offer_clicked = true;
          window.__cpa_locker_human_sequence_busy = false;
          if (chosen.normKey) {
            window.__cpa_clicked_offer_keys[chosen.normKey] = Date.now();
          }

          if (window.__cpa_locker_poll_interval) {
            clearInterval(window.__cpa_locker_poll_interval);
            window.__cpa_locker_poll_interval = null;
          }
          if (window.__cpa_locker_mutation_obs) {
            try { window.__cpa_locker_mutation_obs.disconnect(); } catch(e) {}
            window.__cpa_locker_mutation_obs = null;
          }

          emitHumanStatus('👆 نقر بشري واقعي على العرض: «' + shortTitle + '» والانتقال لتبويب العرض...');
          if (window.AndroidBridge && typeof window.AndroidBridge.onLockerStatus === 'function') {
            try { window.AndroidBridge.onLockerStatus('✅ تم النقر بشرياً على: ' + shortTitle + ' ➔ جاري فتح تبويب العرض...'); } catch(e) {}
          }

          var el = chosen.element;
          var evInit = { bubbles: true, cancelable: true, view: window, clientX: targetX, clientY: targetY };
          try {
            el.dispatchEvent(new PointerEvent('pointerover', evInit));
            el.dispatchEvent(new MouseEvent('mouseover', evInit));
            el.dispatchEvent(new PointerEvent('pointerdown', evInit));
            el.dispatchEvent(new MouseEvent('mousedown', evInit));
            if (typeof el.focus === 'function') el.focus();
          } catch(e) {}

          var pressHoldMs = 85 + Math.floor(Math.random() * 65);
          setTimeout(function() {
            // Re-resolve offerUrl in case locker script updated href on mousedown/pointerdown!
            var finalOfferUrl = resolveOfferUrl(chosen.href, el) || offerUrl;

            // Open the managed Offer Tab first if we have a direct URL so BrowserScreen's 3-second deduplicator
            // is primed before the DOM click fires (preventing duplicate popup tabs while still firing the locker's JS click listener!)
            var openedViaBridge = false;
            if (finalOfferUrl && finalOfferUrl.indexOf('http') === 0 && openNewTab && window.AndroidBridge && typeof window.AndroidBridge.onOfferClickedInNewTab === 'function') {
              try {
                window.AndroidBridge.onOfferClickedInNewTab(offerTitle, finalOfferUrl);
                openedViaBridge = true;
              } catch(e) {}
            }

            // Always dispatch pointerup/mouseup/click on the element so the Locker's internal JS lead-check spinner activates!
            try {
              var origTarget = el.getAttribute ? el.getAttribute('target') : null;
              var origHref = el.getAttribute ? el.getAttribute('href') : null;
              if (openedViaBridge && el.tagName === 'A') {
                // Temporarily prevent current tab navigation while letting onclick / event listeners run
                var preventDefaultHandler = function(ev) { ev.preventDefault(); };
                el.addEventListener('click', preventDefaultHandler, { once: true });
              }
              el.dispatchEvent(new PointerEvent('pointerup', evInit));
              el.dispatchEvent(new MouseEvent('mouseup', evInit));
              el.dispatchEvent(new MouseEvent('click', evInit));
              if (!openedViaBridge && typeof el.click === 'function') {
                el.click();
              }
            } catch(clickErr) {
              if (!openedViaBridge) {
                try { if (el.click) el.click(); } catch(e) {}
              }
            }

            if (!openedViaBridge) {
              if (finalOfferUrl && finalOfferUrl.indexOf('http') === 0) {
                if (window.AndroidBridge && typeof window.AndroidBridge.onOfferClicked === 'function') {
                  try { window.AndroidBridge.onOfferClicked(offerTitle, finalOfferUrl); } catch(e) {}
                }
                if (!openNewTab && finalOfferUrl !== window.location.href) {
                  setTimeout(function() { window.location.href = finalOfferUrl; }, 400);
                }
              } else {
                if (window.AndroidBridge && typeof window.AndroidBridge.onLockerStatus === 'function') {
                  try { window.AndroidBridge.onLockerStatus('🔓 تم النقر البشري داخل اللوكر — بانتظار فتح تبويب العرض...'); } catch(e) {}
                }
              }
            }
          }, pressHoldMs);
        }, steps * 32 + 160);
      }, compareDelay);

      return true;
    }

    function selectBestOfferCandidate(candidates) {
      if (!candidates || candidates.length === 0) return { chosen: null, matchedUserPriority: false };

      // Filter out already-clicked offers if unclicked alternatives exist
      var unclicked = candidates.filter(function(c) {
        return !c.normKey || !window.__cpa_clicked_offer_keys[c.normKey];
      });
      var pool = unclicked.length > 0 ? unclicked : candidates;

      if (strategy === 'random') {
        var rIdx = Math.floor(Math.random() * pool.length);
        return { chosen: pool[rIdx], matchedUserPriority: false };
      }
      if (strategy === 'first') {
        return { chosen: pool[0], matchedUserPriority: false };
      }

      // Smart Priority + Conversion Ease Ranking:
      // 1. Check user's priority targetList in order; if multiple candidates match a priority target, pick the one with highest easeScore
      for (var p = 0; p < targetList.length; p++) {
        var targetKw = targetList[p];
        var matchingCands = pool.filter(function(c) { return fuzzyOfferMatch(c.text, targetKw); });
        if (matchingCands.length > 0) {
          matchingCands.sort(function(a, b) { return (b.easeScore || 0) - (a.easeScore || 0); });
          return { chosen: matchingCands[0], matchedUserPriority: true };
        }
      }

      // 2. Fallback: Sort all available locker offers by Conversion Ease Score (SOI/Email > Gift Card > Survey > App Install > PIN)
      var sortedByEase = pool.slice().sort(function(a, b) {
        return (b.easeScore || 0) - (a.easeScore || 0);
      });
      return { chosen: sortedByEase[0], matchedUserPriority: false };
    }

    function scanAndExecuteOfferSelection() {
      if (window.__cpa_locker_offer_clicked || window.__cpa_locker_human_sequence_busy) return true;
      dismissLandingOverlays();
      var candidates = collectAllCandidates();
      if (candidates.length === 0) return false;

      // Wait one settle cycle (450ms) when offers first appear so all locker offers finish rendering before ranking
      if (!window.__cpa_locker_offers_first_seen_at) {
        window.__cpa_locker_offers_first_seen_at = Date.now();
        emitHumanStatus('🔓 رصد ظهور عروض اللوكر — انتظار اكتمال تحميل القائمة للمفاضلة الذكية...');
        return false;
      }
      if (Date.now() - window.__cpa_locker_offers_first_seen_at < 420 && candidates.length < 3) {
        return false;
      }

      var sel = selectBestOfferCandidate(candidates);
      var chosen = sel.chosen || candidates[0];
      reportOffersForLearning(candidates, sel.matchedUserPriority);
      return executeHumanLockerOfferSequence(chosen, candidates);
    }

    // Start continuous smart watcher (polls every 420ms + MutationObserver)
    if (!window.__cpa_locker_scanner_active) {
      window.__cpa_locker_scanner_active = true;
      if (window.AndroidBridge && typeof window.AndroidBridge.onLockerStatus === 'function') {
        window.AndroidBridge.onLockerStatus('🔍 مراقبة ذكية وبشرية لظهور عروض اللوكر...');
      }

      var attempts = 0;
      var maxAttempts = 85; // ~35 seconds of persistent watching
      window.__cpa_locker_poll_interval = setInterval(function() {
        attempts++;
        if (window.__cpa_locker_offer_clicked || attempts > maxAttempts) {
          clearInterval(window.__cpa_locker_poll_interval);
          window.__cpa_locker_poll_interval = null;
          window.__cpa_locker_scanner_active = false;
          return;
        }
        scanAndExecuteOfferSelection();
      }, 420);

      try {
        var obs = new MutationObserver(function() {
          if (window.__cpa_locker_offer_clicked) {
            obs.disconnect();
            return;
          }
          scanAndExecuteOfferSelection();
        });
        obs.observe(document.documentElement || document.body, { childList: true, subtree: true });
        window.__cpa_locker_mutation_obs = obs;
      } catch(obsErr) {}
    } else {
      scanAndExecuteOfferSelection();
    }

    return 'smart_human_locker_watcher_active';
  } catch(e) {
    return 'error:' + e.message;
  }
})();
true;
        """.trimIndent()
    }

    /**
     * Simulates natural human behavior on a newly opened offer tab:
     * - Progressive smooth scrolling
     * - Reading pauses
     * - Inspection of lead gen elements (email submit, survey question, postal code)
     * - Notifies Android when interaction session finishes
     */
    fun buildHumanInteractionOnOfferPageScript(
        tabId: String,
        stayDurationSec: Int = 15
    ): String {
        val safeTabId = tabId.replace("'", "\\'")
        val duration = stayDurationSec.coerceAtLeast(5)

        return """
(function() {
  try {
    if (window.__cpa_new_tab_interaction_started) return 'already_started';
    window.__cpa_new_tab_interaction_started = true;

    var tabId = '$safeTabId';
    var maxDurationMs = $duration * 1000;
    var startTime = Date.now();

    function reportNewTabHumanStatus(msg) {
      try {
        if (window.AndroidBridge && typeof window.AndroidBridge.onHumanActionStatus === 'function') {
          window.AndroidBridge.onHumanActionStatus(msg);
        }
      } catch(e) {}
    }

    reportNewTabHumanStatus('👁️ استكشاف بصري وقراءة عنوان صفحة العرض الجديد...');

    var stepIndex = 0;
    var scrollTimer = setInterval(function() {
      var elapsed = Date.now() - startTime;
      if (elapsed >= maxDurationMs) {
        clearInterval(scrollTimer);
        finishInteraction();
        return;
      }

      stepIndex++;
      var scrollAmount = Math.floor(Math.random() * 180) + 65;
      window.scrollBy({ top: scrollAmount, behavior: 'smooth' });

      // Simulate natural mouse/gaze movement along with reading scroll
      try {
        var gx = Math.floor((window.innerWidth || 360) * (0.25 + Math.random() * 0.5));
        var gy = Math.floor((window.innerHeight || 640) * (0.25 + Math.random() * 0.5));
        document.dispatchEvent(new PointerEvent('pointermove', { clientX: gx, clientY: gy, bubbles: true }));
        document.dispatchEvent(new MouseEvent('mousemove', { clientX: gx, clientY: gy, bubbles: true }));
      } catch(e) {}

      var remainingSec = Math.max(1, Math.ceil((maxDurationMs - elapsed) / 1000));
      if (stepIndex % 2 === 1) {
        reportNewTabHumanStatus('📜 تمرير بشري تدريجي وتتبع بصري لصفحة العرض (متبقي ' + remainingSec + ' ثانية)...');
      } else {
        reportNewTabHumanStatus('🧠 فحص بصري لحقول النموذج والأسئلة بمعايير بشرية (متبقي ' + remainingSec + ' ثانية)...');
      }

      if (Math.random() < 0.25) {
        setTimeout(function() {
          window.scrollBy({ top: -Math.floor(Math.random() * 85 + 25), behavior: 'smooth' });
        }, 420);
      }
    }, 1250);

    function finishInteraction() {
      var inputs = Array.from(document.querySelectorAll('input:not([type=hidden])'));
      var emailField = inputs.find(function(i) {
        var t = (i.type || '').toLowerCase();
        var m = (i.name || '') + ' ' + (i.id || '') + ' ' + (i.placeholder || '');
        return t === 'email' || m.toLowerCase().includes('email');
      });
      var zipField = inputs.find(function(i) {
        var m = (i.name || '') + ' ' + (i.id || '') + ' ' + (i.placeholder || '');
        return m.toLowerCase().includes('zip') || m.toLowerCase().includes('postal');
      });
      var radios = document.querySelectorAll('input[type=radio], input[type=checkbox]');

      var details = 'تصفح بشري مكتمل (${duration}s)';
      if (emailField) details += ' • رصد حقل إيميل';
      if (zipField) details += ' • رصد حقل الرمز البريدي';
      if (radios.length > 0) details += ' • خيارات استبيان (' + radios.length + ')';

      if (window.AndroidBridge && typeof window.AndroidBridge.onNewTabInteractionCompleted === 'function') {
        window.AndroidBridge.onNewTabInteractionCompleted(tabId, window.location.href, details);
      }
    }
  } catch(e) {
  }
})();
true;
        """.trimIndent()
    }

    /**
     * Deep Content Locker Detector, Force-Activator & Smart Offer Text Harvester.
     * Triggered when the user clicks the "Detect Locker & Extract Offers" button in the Browser toolbar
     * (or from the Offer Click screen).
     *
     * Capabilities:
     * 1. Detects all known Locker scripts, iframes, and modal containers (CPAGrip, AlignmentFiles,
     *    OGAds, CPABuild, AdBlueMedia, FileTrkr, CPALead, custom HTML lockers).
     * 2. Automatically triggers call_locker() / DisplayInlineBox() / og_load() / CPABuildLock() and unhides
     *    locker overlays so hidden offers become rendered in the DOM.
     * 3. Broadcasts postMessage({ type: 'CPA_EXTRACT_LOCKER_OFFERS' }) to all cross-origin subframes
     *    so iframe-hosted lockers also extract and return their offer lists.
     * 4. Performs multi-pass extraction (0ms, 650ms, 1450ms, 2500ms), cleans noise, classifies each offer
     *    by brand/reward & conversion difficulty, and dispatches results to AndroidBridge.onLockerOffersExtracted().
     */
    fun buildLockerOfferExtractorScript(
        customId: String = "1741238",
        forceActivateLocker: Boolean = true
    ): String {
        val safeCustomId = customId.trim().ifBlank { "1741238" }.replace("'", "\\'")
        return """
(function() {
  try {
    var defaultId = '$safeCustomId';
    var forceActivate = $forceActivateLocker;

    function detectAndActivateLocker() {
      var detectedUrl = '';
      var detectedId = '';
      var isLockerFound = false;

      // 1. Scan <script> tags
      var scriptTags = Array.from(document.querySelectorAll('script[src]'));
      for (var i = 0; i < scriptTags.length; i++) {
        var src = scriptTags[i].src || '';
        var srcLow = src.toLowerCase();
        if (srcLow.indexOf('script_include.php') !== -1 ||
            srcLow.indexOf('alignmentfiles') !== -1 ||
            srcLow.indexOf('cpagrip') !== -1 ||
            srcLow.indexOf('filetrkr') !== -1 ||
            srcLow.indexOf('ogads') !== -1 ||
            srcLow.indexOf('cpabuild') !== -1 ||
            srcLow.indexOf('adbluemedia') !== -1 ||
            srcLow.indexOf('cpalead') !== -1 ||
            srcLow.indexOf('adworkmedia') !== -1 ||
            srcLow.indexOf('locker') !== -1) {
          detectedUrl = src;
          isLockerFound = true;
          var m = src.match(/[?&]id=([0-9a-zA-Z_-]+)/i);
          if (m && m[1]) detectedId = m[1];
          break;
        }
      }

      // 2. Scan raw HTML if not in script[src]
      if (!detectedUrl && document.documentElement) {
        var html = document.documentElement.innerHTML || '';
        var rawMatch = html.match(/(https?:\/\/[^\s"'<>]*(?:alignmentfiles\.com|cpagrip\.com|filetrkr\.com|ogads\.com|cpabuild\.com|adbluemedia\.com)[^\s"'<>]*script_include\.php\?id=([0-9a-zA-Z_-]+))/i);
        if (rawMatch) {
          detectedUrl = rawMatch[1];
          detectedId = rawMatch[2];
          isLockerFound = true;
        }
      }

      // 3. Scan iframes for locker URLs
      var iframes = Array.from(document.querySelectorAll('iframe'));
      for (var f = 0; f < iframes.length; f++) {
        var fSrc = (iframes[f].src || iframes[f].getAttribute('data-src') || '').trim();
        var fLow = fSrc.toLowerCase();
        if (fLow.indexOf('load_box') !== -1 ||
            fLow.indexOf('alignmentfiles') !== -1 ||
            fLow.indexOf('cpagrip') !== -1 ||
            fLow.indexOf('ogads') !== -1 ||
            fLow.indexOf('cpabuild') !== -1 ||
            fLow.indexOf('filetrkr') !== -1 ||
            fLow.indexOf('locker') !== -1 ||
            iframes[f].id === 'the_box' ||
            iframes[f].id === 'InlineBoxFrame') {
          isLockerFound = true;
          if (!detectedUrl) detectedUrl = fSrc;
          var fm = fSrc.match(/[?&]id=([0-9a-zA-Z_-]+)/i);
          if (fm && fm[1] && !detectedId) detectedId = fm[1];
        }
      }

      // 4. Trigger locker global functions if present
      if (forceActivate) {
        var triggerFns = [
          'call_locker', 'DisplayInlineBox', 'call1', 'call2', 'load_slidepage',
          'og_load', 'CPABuildLock', 'show_locker', 'open_locker', 'load_locker',
          'start_locker', 'cpa_complete'
        ];
        for (var t = 0; t < triggerFns.length; t++) {
          var fnName = triggerFns[t];
          if (typeof window[fnName] === 'function') {
            isLockerFound = true;
            try { window[fnName](); } catch(e) {}
          }
        }

        // Also click any obvious "Unlock / Verify / Claim / Open Locker" trigger button if no offers are visible yet
        var lockerDomCheck = document.querySelector('#InlineBoxMainOuterLayer, #InlineBoxDiv, #the_box, .offer_link, [class*="locker-offers"]');
        if (!lockerDomCheck) {
          var triggerBtns = document.querySelectorAll('[onclick*="call_locker"], [onclick*="locker"], a[href*="call_locker"], button[id*="locker" i], button[class*="locker" i], a[class*="locker" i]');
          for (var b = 0; b < triggerBtns.length; b++) {
            isLockerFound = true;
            try { triggerBtns[b].click(); } catch(e) {}
          }
        }
      }

      // 5. Unhide any locker containers/iframes
      var lockerSelectors = [
        '#InlineBoxMainOuterLayer', '#InlineBoxDiv', '#the_box', 'div[id*="InlineBox"]',
        'iframe[src*="alignmentfiles"]', 'iframe[src*="load_box"]', 'iframe[src*="cpagrip"]',
        'iframe[src*="ogads"]', 'iframe[src*="cpabuild"]', 'iframe[src*="locker"]',
        'div[class*="cpa_locker"]', 'div[id*="cpa_locker"]', 'div[class*="locker-offers"]',
        'div[id*="locker-offers"]', '.locker-overlay', '#locker-modal'
      ];
      lockerSelectors.forEach(function(sel) {
        document.querySelectorAll(sel).forEach(function(el) {
          isLockerFound = true;
          try {
            el.style.display = 'block';
            el.style.visibility = 'visible';
            el.style.opacity = '1';
            el.style.zIndex = '2147483647';
          } catch(e) {}
        });
      });

      // Broadcast postMessage to all subframes so cross-origin locker iframes extract their offers immediately
      try {
        for (var w = 0; w < window.frames.length; w++) {
          try {
            window.frames[w].postMessage({ type: 'CPA_EXTRACT_LOCKER_OFFERS', forceScan: true }, '*');
          } catch(e) {}
        }
      } catch(e) {}

      return {
        isLockerFound: isLockerFound,
        lockerUrl: detectedUrl || window.location.href || '',
        lockerId: detectedId || (isLockerFound ? defaultId : 'page_scanner')
      };
    }

    function cleanOfferText(raw) {
      if (!raw) return '';
      var s = ('' + raw)
        .replace(/[\r\n\t]+/g, ' ')
        .replace(/\s+/g, ' ')
        .trim();
      // Remove leading bullet numbers like "1. " or "• " or "👉 "
      s = s.replace(/^(?:[0-9]{1,2}[\.\)\-\:]\s*|[•▸►➔➜★☆⚡🔥🎁👉]+\s*)+/i, '').trim();
      // Remove trailing CTA clutter if attached by newline in parent container (e.g. "Get $100 Nike Gift Card Free")
      return s;
    }

    function isNoiseOrJunkText(txt) {
      if (!txt || txt.length < 4 || txt.length > 135) return true;
      var low = txt.toLowerCase().trim();
      if (/^https?:\/\//i.test(low) || /^www\./i.test(low)) return true;
      if (/^[0-9\s\$\.,%\-\+\(\)]+$/.test(low)) return true;

      var exactJunk = [
        'close', 'cancel', 'back', 'next', 'continue', 'submit', 'ok', 'yes', 'no',
        'privacy policy', 'privacy', 'terms of service', 'terms & conditions', 'terms',
        'dmca', 'contact us', 'contact', 'help', 'faq', 'about us', 'login', 'sign in',
        'sign up', 'register', 'skip', 'loading', 'loading...', 'please wait',
        'click here', 'read more', 'learn more', 'home', 'menu', 'search',
        'إغلاق', 'رجوع', 'التالي', 'إلغاء', 'سياسة الخصوصية', 'الشروط والأحكام'
      ];
      if (exactJunk.indexOf(low) !== -1) return true;

      var junkSubstrings = [
        'powered by cpagrip', 'powered by ogads', 'complete an offer below',
        'complete one of the offers', 'human verification required', 'verify you are human',
        'choose an offer below', 'unlock content', 'all rights reserved', 'copyright ©',
        'cookie policy', 'unsubscribe', 'report abuse', 'javascript:void'
      ];
      for (var i = 0; i < junkSubstrings.length; i++) {
        if (low.indexOf(junkSubstrings[i]) !== -1) return true;
      }
      return false;
    }

    function classifyOfferMeta(txt, href) {
      var low = (txt + ' ' + (href || '')).toLowerCase();
      var brand = '';
      var brandMap = [
        { k: 'nike', label: 'Nike' },
        { k: 'walmart', label: 'Walmart' },
        { k: 'cash app', label: 'Cash App' },
        { k: 'cashapp', label: 'Cash App' },
        { k: 'amazon', label: 'Amazon' },
        { k: 'target', label: 'Target' },
        { k: 'apple', label: 'Apple' },
        { k: 'iphone', label: 'iPhone' },
        { k: 'samsung', label: 'Samsung' },
        { k: 'paypal', label: 'PayPal' },
        { k: 'shein', label: 'SHEIN' },
        { k: 'temu', label: 'Temu' },
        { k: 'ebay', label: 'eBay' },
        { k: 'netflix', label: 'Netflix' },
        { k: 'roblox', label: 'Roblox' },
        { k: 'playstation', label: 'PSN' },
        { k: 'xbox', label: 'Xbox' },
        { k: 'google play', label: 'Google Play' },
        { k: 'visa', label: 'Visa' },
        { k: 'mastercard', label: 'Mastercard' },
        { k: 'starbucks', label: 'Starbucks' },
        { k: 'mcdonald', label: 'McDonalds' },
        { k: 'aldi', label: 'Aldi' },
        { k: 'tesco', label: 'Tesco' },
        { k: 'asda', label: 'Asda' },
        { k: 'costco', label: 'Costco' }
      ];
      for (var b = 0; b < brandMap.length; b++) {
        if (low.indexOf(brandMap[b].k) !== -1) {
          brand = brandMap[b].label;
          break;
        }
      }
      var amountMatch = txt.match(/(\$\s*[0-9,]+|[0-9,]+\s*\$|€\s*[0-9,]+|£\s*[0-9,]+)/);
      if (amountMatch && amountMatch[1]) {
        var amt = amountMatch[1].replace(/\s+/g, '');
        brand = brand ? (brand + ' ' + amt) : amt;
      }

      var categoryTag = 'Locker Offer 🔒';
      var priorityScore = 70;

      if (low.indexOf('email') !== -1 || low.indexOf('zip') !== -1 || low.indexOf('enter your') !== -1) {
        categoryTag = 'Email / SOI 📧';
        priorityScore = 96;
      } else if (low.indexOf('gift card') !== -1 || low.indexOf('reward') !== -1 || low.indexOf('claim') !== -1 || low.indexOf('win ') !== -1 || low.indexOf('get a $') !== -1 || low.indexOf('get $') !== -1) {
        categoryTag = 'Gift Card / Reward 🎁';
        priorityScore = 92;
      } else if (low.indexOf('survey') !== -1 || low.indexOf('quiz') !== -1 || low.indexOf('opinion') !== -1 || low.indexOf('question') !== -1) {
        categoryTag = 'Survey / Quiz 📊';
        priorityScore = 85;
      } else if (low.indexOf('install') !== -1 || low.indexOf('download') !== -1 || low.indexOf('app') !== -1 || low.indexOf('play.google') !== -1) {
        categoryTag = 'App / Download 📲';
        priorityScore = 65;
      } else if (low.indexOf('pin') !== -1 || low.indexOf('sms') !== -1 || low.indexOf('mobile number') !== -1) {
        categoryTag = 'PIN / Mobile 📱';
        priorityScore = 60;
      }

      if (brand) priorityScore = Math.min(100, priorityScore + 4);

      return {
        brandTag: brand,
        categoryTag: categoryTag,
        priorityScore: priorityScore
      };
    }

    function extractOffersFromDocument(doc, isIframe, outList, seenMap) {
      if (!doc) return;
      var strictLockerSelectors = [
        '#InlineBoxDiv a',
        '#the_box a',
        '#main_div a',
        '.offer_link',
        '.offer-link',
        '.locker_offer',
        '.locker-offer',
        '[class*="offer-item"] a',
        '[class*="offer_item"] a',
        '[class*="offer-row"] a',
        '[class*="offer_row"] a',
        'div[class*="offer"] a',
        'li[id*="offer"] a',
        'li[class*="offer"] a',
        'tr[class*="offer"] a',
        'table[class*="offer"] a',
        '[data-offer-id] a',
        '[data-offer-id]',
        'a[href*="tracking"]',
        'a[href*="load_box"]',
        'a[href*="show_file"]',
        'a[href*="click.php"]',
        'a[href*="alignmentfiles"]',
        'a[href*="cpagrip"]',
        'a[href*="ogads"]',
        'a[href*="cpabuild"]',
        'a[href*="adbluemedia"]',
        'a[href*="filetrkr"]',
        'a[href*="offer_id="]',
        'a[href*="aff_id="]',
        'a[class*="offer"]',
        'a[id*="offer"]',
        'button[class*="offer"]',
        'button[onclick*="offer"]'
      ];

      function addElementOffer(el, isStrictLockerItem) {
        if (!el) return;
        // Prefer specific title child element if present inside an offer card/row
        var titleEl = el.querySelector ? el.querySelector('.offer_title, .offer-title, .title, .name, h3, h4, strong, b, span[class*="title"]') : null;
        var rawText = '';
        if (titleEl && (titleEl.innerText || titleEl.textContent || '').trim().length >= 4) {
          rawText = (titleEl.innerText || titleEl.textContent || '').trim();
        } else {
          rawText = (el.innerText || el.textContent || el.getAttribute('title') || el.getAttribute('aria-label') || '').trim();
        }
        var clean = cleanOfferText(rawText);
        if (isNoiseOrJunkText(clean)) return;

        var normKey = clean.toLowerCase().replace(/[^a-z0-9\u0600-\u06FF$]+/g, '');
        if (!normKey || normKey.length < 3 || seenMap[normKey]) return;

        var rawHref = el.href || (el.getAttribute ? (el.getAttribute('href') || el.getAttribute('data-url') || el.getAttribute('data-href')) : '') || '';
        if (!rawHref && el.getAttribute && el.getAttribute('onclick')) {
          var m = el.getAttribute('onclick').match(/https?:\/\/[^\s'"]+/);
          if (m) rawHref = m[0];
        }
        if (rawHref && rawHref.indexOf('http') !== 0 && rawHref.indexOf('javascript:') !== 0 && rawHref !== '#') {
          try { rawHref = new URL(rawHref, doc.baseURI || window.location.href).toString(); } catch(e) {}
        }
        if (rawHref && (rawHref.indexOf('script_include.php') !== -1 || rawHref.indexOf('load_box.php') !== -1)) {
          rawHref = '';
        }

        seenMap[normKey] = true;
        var meta = classifyOfferMeta(clean, rawHref);
        outList.push({
          text: clean,
          href: rawHref || '',
          categoryTag: meta.categoryTag,
          brandTag: meta.brandTag,
          priorityScore: isStrictLockerItem ? Math.min(100, meta.priorityScore + 3) : meta.priorityScore,
          isIframe: !!isIframe
        });
      }

      try {
        var strictFound = doc.querySelectorAll(strictLockerSelectors.join(','));
        for (var k = 0; k < strictFound.length; k++) {
          addElementOffer(strictFound[k], true);
        }
      } catch(e) {}

      // Fallback: if no strict locker offers matched in this document, scan interactive offer-like links/buttons
      if (outList.length === 0) {
        var offerHintWords = [
          'gift card', 'reward', 'claim', 'win ', 'get $', 'get a $', '$100', '$500', '$750', '$1000',
          'nike', 'walmart', 'cash app', 'amazon', 'paypal', 'target', 'apple', 'iphone', 'samsung',
          'survey', 'free', 'bonus', 'voucher', 'coupon', 'offer', 'unlock', 'verify', 'download', 'install'
        ];
        var generalEls = doc.querySelectorAll('a[href], button, [role="button"], div[onclick], li[onclick]');
        for (var g = 0; g < generalEls.length; g++) {
          var gel = generalEls[g];
          var gTxt = cleanOfferText(gel.innerText || gel.textContent || gel.getAttribute('title') || '');
          if (isNoiseOrJunkText(gTxt)) continue;
          var gLow = gTxt.toLowerCase();
          var gHref = (gel.href || (gel.getAttribute ? gel.getAttribute('href') : '') || '').toLowerCase();
          var hasOfferHint = offerHintWords.some(function(w) { return gLow.indexOf(w) !== -1; }) ||
                             gHref.indexOf('offer') !== -1 || gHref.indexOf('track') !== -1 || gHref.indexOf('click') !== -1;
          if (hasOfferHint) {
            addElementOffer(gel, false);
          }
        }
      }
    }

    var lastReportedCount = -1;

    function runExtractionPass(passNumber) {
      var lockerInfo = detectAndActivateLocker();
      var extractedOffers = [];
      var seenMap = {};

      // Scan top document
      extractOffersFromDocument(document, false, extractedOffers, seenMap);

      // Scan accessible same-origin iframes
      var iframes = document.querySelectorAll('iframe');
      for (var f = 0; f < iframes.length; f++) {
        try {
          var iframeDoc = iframes[f].contentDocument || (iframes[f].contentWindow ? iframes[f].contentWindow.document : null);
          if (iframeDoc) {
            extractOffersFromDocument(iframeDoc, true, extractedOffers, seenMap);
          }
        } catch(crossOriginErr) {
          // Handled by buildCrossOriginSubframeWorkerScript via postMessage / AndroidBridge
        }
      }

      // Sort offers by priorityScore descending so the best offers are saved first
      extractedOffers.sort(function(a, b) { return (b.priorityScore || 0) - (a.priorityScore || 0); });

      if (extractedOffers.length > 0 || passNumber >= 3) {
        if (extractedOffers.length !== lastReportedCount || passNumber === 0 || passNumber === 3) {
          lastReportedCount = extractedOffers.length;
          if (window.AndroidBridge && typeof window.AndroidBridge.onLockerOffersExtracted === 'function') {
            var payload = JSON.stringify({
              lockerId: lockerInfo.lockerId,
              lockerUrl: lockerInfo.lockerUrl,
              isLockerDetected: lockerInfo.isLockerFound || extractedOffers.length > 0,
              isManualTrigger: true,
              passNumber: passNumber,
              offers: extractedOffers
            });
            window.AndroidBridge.onLockerOffersExtracted(payload);
          }
          if (window.AndroidBridge && typeof window.AndroidBridge.onLockerDetectedWithCount === 'function' && (lockerInfo.isLockerFound || extractedOffers.length > 0)) {
            window.AndroidBridge.onLockerDetectedWithCount(
              lockerInfo.lockerUrl,
              lockerInfo.lockerId,
              true,
              extractedOffers.length
            );
          }
        }
      }
      return extractedOffers.length;
    }

    // Immediate pass + delayed passes to catch AJAX / dynamically rendered locker boxes after call_locker()
    var immediateCount = runExtractionPass(0);
    setTimeout(function() { runExtractionPass(1); }, 650);
    setTimeout(function() { runExtractionPass(2); }, 1450);
    setTimeout(function() { runExtractionPass(3); }, 2500);

    return 'locker_extractor_started:' + immediateCount;
  } catch(e) {
    return 'error:' + e.message;
  }
})();
true;
        """.trimIndent()
    }

    /**
     * Injected via WebViewCompat.addDocumentStartJavaScript(..., setOf("*")) so it runs inside
     * cross-origin iframes (such as alignmentfiles.com/load_box.php, cpagrip, ogads, or embedded
     * survey/form iframes) where evaluateJavascript() cannot reach due to Same-Origin Policy.
     */
    fun buildCrossOriginSubframeWorkerScript(): String {
        return """
(function() {
  try {
    if (window === window.top) return;
    if (window.__cpaSubframeWorkerInstalled) return;
    window.__cpaSubframeWorkerInstalled = true;

    function emitSubframeHumanStatus(msg) {
      try {
        if (window.AndroidBridge && typeof window.AndroidBridge.onHumanActionStatus === 'function') {
          window.AndroidBridge.onHumanActionStatus(msg);
        }
      } catch(e) {}
    }

    function cleanSubframeOfferText(raw) {
      if (!raw) return '';
      return ('' + raw)
        .replace(/[\r\n\t]+/g, ' ')
        .replace(/\s+/g, ' ')
        .replace(/^(?:[0-9]{1,2}[\.\)\-\:]\s*|[•▸►➔➜★☆⚡🔥🎁👉]+\s*)+/i, '')
        .trim();
    }

    function isSubframeJunkText(txt) {
      if (!txt || txt.length < 4 || txt.length > 135) return true;
      var low = txt.toLowerCase().trim();
      var junk = ['close', 'cancel', 'privacy policy', 'privacy', 'terms', 'dmca', 'help', 'contact', 'powered by', 'complete an offer', 'human verification'];
      for (var i = 0; i < junk.length; i++) {
        if (low === junk[i] || low.indexOf('powered by ') !== -1 || low.indexOf('complete an offer below') !== -1) return true;
      }
      return false;
    }

    function scoreSubframeOffer(txt, href) {
      var low = ((txt || '') + ' ' + (href || '')).toLowerCase();
      var score = 75;
      var categoryTag = 'Iframe Locker 🔒';
      var brandTag = '';

      var brands = [
        { k: 'nike', b: 'Nike', bonus: 14 },
        { k: 'walmart', b: 'Walmart', bonus: 12 },
        { k: 'cash app', b: 'Cash App', bonus: 12 },
        { k: 'cashapp', b: 'Cash App', bonus: 12 },
        { k: 'amazon', b: 'Amazon', bonus: 11 },
        { k: 'target', b: 'Target', bonus: 10 },
        { k: 'paypal', b: 'PayPal', bonus: 10 },
        { k: 'apple', b: 'Apple', bonus: 9 },
        { k: 'shein', b: 'SHEIN', bonus: 9 }
      ];
      for (var i = 0; i < brands.length; i++) {
        if (low.indexOf(brands[i].k) !== -1) {
          brandTag = brands[i].b;
          score += brands[i].bonus;
          break;
        }
      }
      if (low.indexOf('email') !== -1 || low.indexOf('zip') !== -1 || low.indexOf('enter your') !== -1 || low.indexOf('soi') !== -1) {
        score += 22;
        categoryTag = 'Email / SOI 📧';
      } else if (low.indexOf('gift card') !== -1 || low.indexOf('reward') !== -1 || low.indexOf('claim') !== -1 || low.indexOf('get a ${'$'}') !== -1 || low.indexOf('get ${'$'}') !== -1) {
        score += 16;
        categoryTag = 'Gift Card / Reward 🎁';
      } else if (low.indexOf('survey') !== -1 || low.indexOf('quiz') !== -1) {
        score += 10;
        categoryTag = 'Survey / Quiz 📊';
      }
      if (low.indexOf('install') !== -1 || low.indexOf('download') !== -1 || low.indexOf('play.google') !== -1) {
        score -= 30;
        categoryTag = 'App Install 📲';
      }
      if (low.indexOf('pin') !== -1 || low.indexOf('sms') !== -1 || low.indexOf('trial') !== -1) {
        score -= 35;
      }
      return {
        score: Math.max(10, Math.min(100, score)),
        categoryTag: categoryTag,
        brandTag: brandTag
      };
    }

    function fuzzySubframeMatch(candRaw, targRaw) {
      if (!candRaw || !targRaw) return false;
      var c = ('' + candRaw).toLowerCase();
      var t = ('' + targRaw).toLowerCase();
      if (c.indexOf(t) !== -1) return true;
      var cleanC = c.replace(/[^a-z0-9${'$'}]+/g, ' ').replace(/\s+/g, ' ').trim();
      var cleanT = t.replace(/[^a-z0-9${'$'}]+/g, ' ').replace(/\s+/g, ' ').trim();
      if (cleanC.indexOf(cleanT) !== -1) return true;
      var tokens = cleanT.split(' ').filter(function(w) { return w.length >= 3 || /\d/.test(w); });
      if (tokens.length >= 2) {
        var hits = 0;
        for (var i = 0; i < tokens.length; i++) { if (cleanC.indexOf(tokens[i]) !== -1) hits++; }
        if (hits >= Math.min(tokens.length, 3)) return true;
      }
      return false;
    }

    function extractAndReportSubframeOffers(isManualTrigger) {
      try {
        if (!window.AndroidBridge || typeof window.AndroidBridge.onLockerOffersExtracted !== 'function') return [];
        var href = (window.location.href || '');
        var hrefLow = href.toLowerCase();
        var isLockerFrame = hrefLow.indexOf('alignmentfiles') !== -1 ||
                            hrefLow.indexOf('load_box') !== -1 ||
                            hrefLow.indexOf('cpagrip') !== -1 ||
                            hrefLow.indexOf('ogads') !== -1 ||
                            hrefLow.indexOf('cpabuild') !== -1 ||
                            hrefLow.indexOf('adbluemedia') !== -1 ||
                            hrefLow.indexOf('filetrkr') !== -1 ||
                            hrefLow.indexOf('locker') !== -1 ||
                            !!document.querySelector('.offer_link, #the_box a, #InlineBoxDiv a, a[href*="click.php"], a[href*="tracking"], [class*="offer"]');

        if (!isLockerFrame && !isManualTrigger) return [];

        var links = Array.from(document.querySelectorAll('.offer_link, #the_box a, #InlineBoxDiv a, [class*="offer"] a, a[href*="click.php"], a[href*="tracking"], a[href], button, [role="button"]'));
        var candidates = [];
        var extractedPayloadOffers = [];
        var seenNorm = {};

        for (var k = 0; k < links.length; k++) {
          var el = links[k];
          if (el.offsetParent === null && el.offsetWidth === 0 && el.offsetHeight === 0) continue;
          var titleEl = el.querySelector ? el.querySelector('.offer_title, .offer-title, .title, .name, h3, h4, strong, b') : null;
          var rawTxt = (titleEl && (titleEl.innerText || titleEl.textContent || '').trim().length >= 4)
            ? (titleEl.innerText || titleEl.textContent || '')
            : ((el.innerText || el.textContent || '') + ' ' + (el.getAttribute('title') || ''));
          var txt = cleanSubframeOfferText(rawTxt);
          var linkHref = el.href || el.getAttribute('href') || el.getAttribute('data-url') || '';
          if (linkHref && linkHref.indexOf('javascript:') !== 0 && linkHref !== '#' && !isSubframeJunkText(txt)) {
            var norm = txt.toLowerCase().replace(/[^a-z0-9\u0600-\u06FF${'$'}]+/g, '');
            if (norm.length >= 3 && !seenNorm[norm]) {
              seenNorm[norm] = true;
              var finalHref = linkHref;
              if (finalHref && finalHref.indexOf('http') !== 0) {
                try { finalHref = new URL(finalHref, window.location.href).toString(); } catch(e) {}
              }
              var meta = scoreSubframeOffer(txt, finalHref);
              candidates.push({
                element: el,
                text: txt,
                normKey: norm,
                href: finalHref || linkHref,
                easeScore: meta.score
              });
              extractedPayloadOffers.push({
                text: txt,
                href: finalHref || '',
                categoryTag: meta.categoryTag,
                brandTag: meta.brandTag,
                priorityScore: meta.score
              });
            }
          }
        }

        if (extractedPayloadOffers.length > 0) {
          var sig = extractedPayloadOffers.map(function(o) { return o.text; }).join('|');
          if (isManualTrigger || window.__cpaLastSubframeOfferSig !== sig) {
            window.__cpaLastSubframeOfferSig = sig;
            var idMatch = href.match(/[?&]id=([0-9a-zA-Z_-]+)/i);
            var lockerId = (idMatch && idMatch[1]) ? idMatch[1] : 'iframe_locker';
            try {
              if (typeof window.AndroidBridge.onLockerDetectedWithCount === 'function') {
                window.AndroidBridge.onLockerDetectedWithCount(href, lockerId, true, extractedPayloadOffers.length);
              }
            } catch(e) {}
            try {
              window.AndroidBridge.onLockerOffersExtracted(JSON.stringify({
                lockerId: lockerId,
                lockerUrl: href,
                isLockerDetected: true,
                isManualTrigger: !!isManualTrigger,
                offers: extractedPayloadOffers
              }));
            } catch(e) {}
          }
        }
        return candidates;
      } catch(e) {
        return [];
      }
    }

    function humanClickSubframeOffer(chosen, allCandidates) {
      if (!chosen || window.__cpaSubframeOfferClicked || window.__cpaSubframeHumanBusy) return;
      window.__cpaSubframeHumanBusy = true;

      var offerTitle = (chosen.text || 'Locker Offer').trim();
      var shortTitle = offerTitle.length > 32 ? offerTitle.substring(0, 32) + '...' : offerTitle;
      emitSubframeHumanStatus('🧐 فحص ومقارنة عروض إطار اللوكر (' + allCandidates.length + ' عروض) بشرياً...');

      var comparePause = 650 + Math.floor(Math.random() * 450);
      if (allCandidates.length > 1) {
        var other = allCandidates.find(function(c) { return c !== chosen; });
        if (other && other.element) {
          try {
            other.element.dispatchEvent(new MouseEvent('mouseover', { bubbles: true }));
          } catch(e) {}
        }
      }

      setTimeout(function() {
        emitSubframeHumanStatus('🎯 التحرك البشري والنقر على عرض الإطار: «' + shortTitle + '»...');
        try {
          if (chosen.element && chosen.element.scrollIntoView) {
            chosen.element.scrollIntoView({ behavior: 'smooth', block: 'center' });
          }
        } catch(e) {}

        var rect = (chosen.element && chosen.element.getBoundingClientRect)
          ? chosen.element.getBoundingClientRect()
          : { left: 100, top: 80, width: 160, height: 38 };
        var tx = Math.round(rect.left + Math.max(18, (rect.width || 140) * (0.3 + Math.random() * 0.4)));
        var ty = Math.round(rect.top + Math.max(10, (rect.height || 34) * (0.3 + Math.random() * 0.4)));

        setTimeout(function() {
          window.__cpaSubframeOfferClicked = true;
          window.__cpaSubframeHumanBusy = false;

          var el = chosen.element;
          var evOpts = { bubbles: true, cancelable: true, view: window, clientX: tx, clientY: ty };
          try {
            el.dispatchEvent(new PointerEvent('pointerover', evOpts));
            el.dispatchEvent(new MouseEvent('mouseover', evOpts));
            el.dispatchEvent(new PointerEvent('pointerdown', evOpts));
            el.dispatchEvent(new MouseEvent('mousedown', evOpts));
            if (typeof el.focus === 'function') el.focus();
          } catch(e) {}

          setTimeout(function() {
            var finalUrl = chosen.href || (el.href || '');
            if (finalUrl && finalUrl.indexOf('http') !== 0) {
              try { finalUrl = new URL(finalUrl, window.location.href).toString(); } catch(e) {}
            }

            var openedNewTab = false;
            if (finalUrl && finalUrl.indexOf('http') === 0 && typeof window.AndroidBridge.onOfferClickedInNewTab === 'function') {
              try {
                window.AndroidBridge.onOfferClickedInNewTab(offerTitle, finalUrl);
                openedNewTab = true;
              } catch(e) {}
            }

            try {
              if (openedNewTab && el.tagName === 'A') {
                el.addEventListener('click', function(ev) { ev.preventDefault(); }, { once: true });
              }
              el.dispatchEvent(new PointerEvent('pointerup', evOpts));
              el.dispatchEvent(new MouseEvent('mouseup', evOpts));
              el.dispatchEvent(new MouseEvent('click', evOpts));
              if (!openedNewTab && typeof el.click === 'function') el.click();
            } catch(e) {
              if (!openedNewTab) {
                try { if (el.click) el.click(); } catch(ex) {}
              }
            }
          }, 95);
        }, 380);
      }, comparePause);
    }

    // Listen for instant extraction or explicit offer click requests from the top-level Browser toolbar
    window.addEventListener('message', function(evt) {
      try {
        if (!evt || !evt.data) return;
        if (evt.data.type === 'CPA_EXTRACT_LOCKER_OFFERS') {
          extractAndReportSubframeOffers(true);
        } else if (evt.data.type === 'CPA_CLICK_LOCKER_OFFER') {
          window.__cpaSubframeOfferClicked = false;
          window.__cpaSubframeHumanBusy = false;
          var cands = extractAndReportSubframeOffers(true);
          if (cands.length > 0) {
            var reqText = (evt.data.targetText || '').trim();
            var match = cands.find(function(c) { return fuzzySubframeMatch(c.text, reqText); }) || cands[0];
            humanClickSubframeOffer(match, cands);
          }
        }
      } catch(e) {}
    }, false);

    function runSubframeCycle() {
      try {
        if (!window.AndroidBridge) return;

        var scanRequested = false;
        try {
          if (typeof window.AndroidBridge.isLockerScanRequested === 'function') {
            scanRequested = !!window.AndroidBridge.isLockerScanRequested();
          }
        } catch(e) {}

        var candidates = extractAndReportSubframeOffers(scanRequested);

        if (typeof window.AndroidBridge.isAutomationRunning !== 'function' || !window.AndroidBridge.isAutomationRunning()) return;

        if (candidates.length > 0 && !window.__cpaSubframeOfferClicked && !window.__cpaSubframeHumanBusy) {
          // Wait briefly on first detection so all iframe offers render
          if (!window.__cpaSubframeFirstSeenAt) {
            window.__cpaSubframeFirstSeenAt = Date.now();
            return;
          }
          if (Date.now() - window.__cpaSubframeFirstSeenAt < 450 && candidates.length < 3) return;

          var activeTarget = '';
          var clickList = [];
          try { activeTarget = (window.AndroidBridge.getActiveClickText() || '').toLowerCase().trim(); } catch(e) {}
          try { clickList = JSON.parse(window.AndroidBridge.getClickTextsJson() || '[]'); } catch(e) {}

          var targets = [];
          if (activeTarget) targets.push(activeTarget);
          for (var i = 0; i < clickList.length; i++) {
            var t = (clickList[i] || '').toLowerCase().trim();
            if (t && targets.indexOf(t) === -1) targets.push(t);
          }
          if (targets.length === 0) {
            targets = ['get a ${'$'}100 nike gift card', 'nike', 'walmart', 'cash app', 'gift card', '${'$'}1000', '${'$'}750', '${'$'}500', 'claim', 'survey', 'reward'];
          }

          var chosen = null;
          for (var p = 0; p < targets.length; p++) {
            var tLow = targets[p];
            var matches = candidates.filter(function(c) { return fuzzySubframeMatch(c.text, tLow); });
            if (matches.length > 0) {
              matches.sort(function(a, b) { return (b.easeScore || 0) - (a.easeScore || 0); });
              chosen = matches[0];
              break;
            }
          }
          if (!chosen) {
            var sorted = candidates.slice().sort(function(a, b) { return (b.easeScore || 0) - (a.easeScore || 0); });
            chosen = sorted[0];
          }

          if (chosen) {
            humanClickSubframeOffer(chosen, candidates);
          }
        }
      } catch(err) {}
    }

    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', function() {
        runSubframeCycle();
        setInterval(runSubframeCycle, 650);
      });
    } else {
      runSubframeCycle();
      setInterval(runSubframeCycle, 650);
    }
  } catch(e) {}
})();
        """.trimIndent()
    }

    /**
     * Specialized Smartlink / Multi-Redirect & Interstitial Recovery Helper.
     * Designed to NEVER interfere with native HTTP 301/302/307 redirects, active JS redirect chains,
     * dynamic token redirects, or normal landing pages.
     * Only activates in the top frame after the page has finished loading (readyState === 'complete')
     * and remained completely stalled on a sparse interstitial/bridge page for >= 3.2 seconds.
     */
    fun buildSmartlinkRedirectHelperScript(): String {
        return """
(function() {
  try {
    if (window !== window.top) return;
    if (window.__cpaSmartlinkHelperActive) return;
    window.__cpaSmartlinkHelperActive = true;

    var initialHref = (window.location.href || '');

    function hasLockerOrFormPresent() {
      var inputsCount = document.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]), select, textarea').length;
      if (inputsCount > 0) return true;
      var lockerEl = document.querySelector('#InlineBoxMainOuterLayer, #InlineBoxDiv, #the_box, .offer_link, iframe[src*="alignmentfiles"], iframe[src*="load_box"], iframe[src*="cpagrip"], iframe[src*="ogads"], iframe[src*="cpabuild"], iframe[src*="locker"]');
      if (lockerEl) return true;
      return false;
    }

    function isStalledSparseBridgePage() {
      if (document.readyState !== 'complete') return false;
      if ((window.location.href || '') !== initialHref) return false;
      if (hasLockerOrFormPresent()) return false;
      var bodyTxt = ((document.body && (document.body.innerText || document.body.textContent)) || '').trim();
      var pathLow = (window.location.pathname || '').toLowerCase();
      var searchLow = (window.location.search || '').toLowerCase();
      var urlLooksLikeBridge =
        pathLow.indexOf('/redirect') !== -1 ||
        pathLow.indexOf('/track') !== -1 ||
        pathLow.indexOf('/click') !== -1 ||
        pathLow.indexOf('/go/') !== -1 ||
        pathLow.indexOf('/out/') !== -1 ||
        searchLow.indexOf('redirect=') !== -1 ||
        searchLow.indexOf('url=http') !== -1 ||
        /\/\d+\/\d+$/.test(pathLow);
      return bodyTxt.length < 240 && (urlLooksLikeBridge || bodyTxt.length < 120);
    }

    function checkStalledBridge() {
      try {
        if ((window.location.href || '') !== initialHref) return;
        if (document.readyState !== 'complete') return;
        if (hasLockerOrFormPresent()) return;

        // 1. Follow stalled <meta http-equiv="refresh"> only if browser hasn't navigated after 3.2+ seconds
        var metas = document.querySelectorAll('meta[http-equiv]');
        for (var m = 0; m < metas.length; m++) {
          var eq = (metas[m].getAttribute('http-equiv') || '').toLowerCase();
          if (eq === 'refresh') {
            var content = metas[m].getAttribute('content') || '';
            var delayMatch = content.match(/^\s*(\d+)/);
            var metaDelaySec = delayMatch ? parseInt(delayMatch[1], 10) : 0;
            var urlMatch = content.match(/url\s*=\s*['"]?([^'";\s]+)['"]?/i);
            if (metaDelaySec <= 6 && urlMatch && urlMatch[1]) {
              var nextUrl = urlMatch[1].trim();
              if (nextUrl.indexOf('//') === 0) nextUrl = 'https:' + nextUrl;
              else if (nextUrl.indexOf('http') !== 0) {
                try { nextUrl = new URL(nextUrl, window.location.href).toString(); } catch(e) {}
              }
              if (nextUrl.indexOf('http') === 0 && nextUrl !== window.location.href) {
                window.location.replace(nextUrl);
                return;
              }
            }
          }
        }

        if (!isStalledSparseBridgePage()) return;

        // 2. If a sparse bridge page has a single explicit "Continue / Proceed / Skip Ad / Click here to continue" button
        var clickables = document.querySelectorAll('a[href], button, [role="button"], input[type="button"], input[type="submit"]');
        var exactBridgePattern = /^(continue|proceed|click here|click here to continue|skip ad|skip|continue to offer|متابعة|اضغط هنا للمتابعة|تخطي الإعلان)$/i;
        for (var i = 0; i < clickables.length; i++) {
          var el = clickables[i];
          if (el.offsetParent === null) continue;
          var txt = ((el.innerText || el.textContent || el.value || '') + '').replace(/\s+/g, ' ').trim();
          if (exactBridgePattern.test(txt)) {
            try { el.click(); } catch(e) {}
            return;
          }
        }
      } catch(e) {}
    }

    // Wait 3.3s and 5.5s so normal multi-hop HTTP/JS redirects (0ms - 3000ms) complete with zero interference!
    setTimeout(checkStalledBridge, 3300);
    setTimeout(checkStalledBridge, 5500);
  } catch(e) {}
})();
true;
        """.trimIndent()
    }
}