package org.barrierfoss.androidclient.protocol

object ProtocolConstants {
    const val PROTOCOL_MAJOR = 1
    const val PROTOCOL_MINOR = 6
    const val DEFAULT_PORT = 24800
    const val HELLO_PREFIX = "Barrier"
    const val MAX_PACKET_SIZE = 4 * 1024 * 1024

    const val MSG_C_NOOP = "CNOP"
    const val MSG_C_CLOSE = "CBYE"
    const val MSG_C_ENTER = "CINN"
    const val MSG_C_LEAVE = "COUT"
    const val MSG_C_CLIPBOARD = "CCLP"
    const val MSG_C_SCREEN_SAVER = "CSEC"
    const val MSG_C_RESET_OPTIONS = "CROP"
    const val MSG_C_INFO_ACK = "CIAK"
    const val MSG_C_KEEP_ALIVE = "CALV"

    const val MSG_D_KEY_DOWN = "DKDN"
    const val MSG_D_KEY_REPEAT = "DKRP"
    const val MSG_D_KEY_UP = "DKUP"
    const val MSG_D_MOUSE_DOWN = "DMDN"
    const val MSG_D_MOUSE_UP = "DMUP"
    const val MSG_D_MOUSE_MOVE = "DMMV"
    const val MSG_D_MOUSE_REL_MOVE = "DMRM"
    const val MSG_D_MOUSE_WHEEL = "DMWM"
    const val MSG_D_CLIPBOARD = "DCLP"
    const val MSG_D_INFO = "DINF"
    const val MSG_D_SET_OPTIONS = "DSOP"
    const val MSG_D_FILE_TRANSFER = "DFTR"
    const val MSG_D_DRAG_INFO = "DDRG"

    const val MSG_Q_INFO = "QINF"

    const val MSG_E_INCOMPATIBLE = "EICV"
    const val MSG_E_BUSY = "EBSY"
    const val MSG_E_UNKNOWN = "EUNK"
    const val MSG_E_BAD = "EBAD"
}
