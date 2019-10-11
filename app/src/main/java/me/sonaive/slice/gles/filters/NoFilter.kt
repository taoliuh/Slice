package me.sonaive.slice.gles.filters

import android.app.Application

/**
 * Created by liutao on 09/10/2019.
 */

class NoFilter(application: Application): GLFilter(application) {

    override fun onCreate() {
        createProgramFromAssetFile("shader/base_vertex.glsl", "shader/base_fragment.glsl")
    }

    override fun onSizeChanged(width: Int, height: Int) {

    }

}