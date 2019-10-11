attribute vec4 aPosition;
attribute vec4 aTextureCoord;
varying vec2 vTextureCoord;
uniform mat4 uMvpMatrix;
uniform mat4 uStMatrix;

void main() {
    gl_Position = uMvpMatrix * aPosition;
    vTextureCoord = (uStMatrix * aTextureCoord).xy;
}
