package com.example.countdowndays.ui.navigation

object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val ADD_EDIT = "add_edit?eventId={eventId}"
    const val DETAIL = "detail/{eventId}"

    const val ARG_EVENT_ID = "eventId"

    fun addEdit(eventId: Long? = null): String =
        if (eventId == null) "add_edit?eventId=" else "add_edit?eventId=$eventId"

    fun detail(eventId: Long): String = "detail/$eventId"
}
