attribute vec4 aPosition;
attribute vec2 aTextureCoord;
uniform mat4 uMvpMatrix;
varying vec2 vTextureCoord;

void main() {
    gl_Position = uMvpMatrix * aPosition;
    vTextureCoord = aTextureCoord;
}
