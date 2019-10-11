precision mediump float;
varying vec2 vTextureCoord;
uniform sampler2D uTexture;

void main() {
    lowp vec4 textureColor = texture2D(uTexture, vTextureCoord);
    float gray = textureColor.r * 0.299 + textureColor.b * 0.114 + textureColor.g * 0.587;
    gl_FragColor = vec4(gray, gray, gray, textureColor.w);
}
