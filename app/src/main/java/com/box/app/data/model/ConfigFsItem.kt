package com.box.app.data.model

sealed class ConfigFsItem(open val name: String, open val path: String) {
    data class Folder(
        override val name: String,
        override val path: String
    ) : ConfigFsItem(name, path)

    data class File(
        override val name: String,
        override val path: String,
        val description: String = ""
    ) : ConfigFsItem(name, path)
}
