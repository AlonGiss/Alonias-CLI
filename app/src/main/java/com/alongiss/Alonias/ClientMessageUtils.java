package com.alongiss.Alonias;

public final class ClientMessageUtils {

    private ClientMessageUtils() {}

    public static String authMessage(String code, String reason) {
        if (reason == null) reason = "";
        reason = reason.trim();

        if ("ALREADY_CONNECTED".equalsIgnoreCase(reason)) {
            return "Ese usuario ya está conectado.";
        }
        if ("BAD_CREDENTIALS".equalsIgnoreCase(reason)) {
            return "Usuario o contraseña incorrectos.";
        }
        if ("USER_EXISTS".equalsIgnoreCase(reason)) {
            return "Ese usuario ya existe.";
        }
        if ("BAD_FORMAT".equalsIgnoreCase(reason)) {
            return "Datos inválidos.";
        }
        if ("NOT_LOGGED".equalsIgnoreCase(reason)) {
            return "Primero tenés que iniciar sesión.";
        }

        return "reg".equalsIgnoreCase(code)
                ? "No se pudo crear la cuenta."
                : "No se pudo iniciar sesión.";
    }

    public static String roomCreateMessage(String reason) {
        if (reason == null) reason = "";
        reason = reason.trim();

        if ("NOT_LOGGED".equalsIgnoreCase(reason)) {
            return "Tenés que iniciar sesión para crear una sala.";
        }
        if ("BAD_FORMAT".equalsIgnoreCase(reason)) {
            return "Los datos de la sala no son válidos.";
        }
        return "No se pudo crear la sala.";
    }

    public static String roomJoinMessage(String reason) {
        if (reason == null) reason = "";
        reason = reason.trim();

        if ("NO_ROOM".equalsIgnoreCase(reason)) {
            return "La sala ya no existe.";
        }
        if ("ROOM_NOT_WAITING".equalsIgnoreCase(reason)) {
            return "La partida ya empezó.";
        }
        if ("BAD_PASSWORD".equalsIgnoreCase(reason)) {
            return "La contraseña es incorrecta.";
        }
        if ("ROOM_FULL".equalsIgnoreCase(reason)) {
            return "La sala está llena.";
        }
        if ("USERNAME_ALREADY_IN_ROOM".equalsIgnoreCase(reason)) {
            return "Ese usuario ya está dentro de esta sala.";
        }
        if ("ALREADY_IN_OTHER_ROOM".equalsIgnoreCase(reason)) {
            return "Ese usuario ya está en otra sala.";
        }
        if ("NO_USER".equalsIgnoreCase(reason)) {
            return "Ese usuario no es válido.";
        }
        if ("NOT_LOGGED".equalsIgnoreCase(reason)) {
            return "Tenés que iniciar sesión primero.";
        }
        if ("BAD_FORMAT".equalsIgnoreCase(reason)) {
            return "No se pudo procesar la solicitud para entrar.";
        }

        return "No se pudo entrar a la sala.";
    }

    public static String lobbyStartMessage(String reason) {
        if (reason == null) reason = "";
        reason = reason.trim();

        if ("NO_ROOM".equalsIgnoreCase(reason)) {
            return "La sala ya no existe.";
        }
        if ("NOT_WAITING".equalsIgnoreCase(reason)) {
            return "La sala ya no está esperando jugadores.";
        }
        if ("NEED_2_PLAYERS".equalsIgnoreCase(reason)) {
            return "Se necesitan al menos 2 jugadores para empezar.";
        }
        if ("NOT_HOST".equalsIgnoreCase(reason)) {
            return "Solo el host puede iniciar la partida.";
        }
        if ("NO_TURN_PLAYERS".equalsIgnoreCase(reason)) {
            return "No hay suficientes jugadores listos para asignar turnos.";
        }
        if ("NO_GAME_MANAGER".equalsIgnoreCase(reason)) {
            return "El servidor todavía no está listo para iniciar la partida.";
        }

        return "No se pudo iniciar la partida.";
    }

    public static String skipMessage(boolean ok, String reason) {
        if (ok) {
            return "Palabra salteada.";
        }

        if (reason == null) reason = "";
        reason = reason.trim();

        if ("NO_GAME".equalsIgnoreCase(reason)) {
            return "La partida ya no está activa.";
        }
        if ("PAUSED".equalsIgnoreCase(reason)) {
            return "La partida está pausada.";
        }
        if ("NOT_EXPLAINER".equalsIgnoreCase(reason)) {
            return "Solo quien explica puede saltar la palabra.";
        }
        if ("NO_GAME_MANAGER".equalsIgnoreCase(reason)) {
            return "El servidor no pudo procesar el skip.";
        }

        return "No se pudo saltar la palabra.";
    }

    public static String guessMessage(boolean correct) {
        return correct ? "¡Correcto!" : "No era esa. Intentá otra vez.";
    }

    public static String genericServerError(String reason) {
        if (reason == null) reason = "";
        reason = reason.trim();

        if ("NOT_LOGGED".equalsIgnoreCase(reason)) {
            return "Tu sesión no está activa.";
        }
        if ("BAD_FORMAT".equalsIgnoreCase(reason)) {
            return "Se enviaron datos inválidos al servidor.";
        }
        if ("UNKNOWN".equalsIgnoreCase(reason)) {
            return "El servidor no entendió la solicitud.";
        }
        if ("BAD".equalsIgnoreCase(reason)) {
            return "La solicitud al servidor era inválida.";
        }

        return "Ocurrió un error de comunicación con el servidor.";
    }

    public static String gameEndedMessage(String reason) {
        if (reason == null) reason = "";
        reason = reason.trim();

        if ("NOT_ENOUGH_PLAYERS".equalsIgnoreCase(reason)) {
            return "La partida terminó porque ya no había suficientes jugadores.";
        }
        if ("PLAYER_LEFT".equalsIgnoreCase(reason)) {
            return "La partida terminó porque un jugador se fue.";
        }
        if ("GAME_OVER".equalsIgnoreCase(reason)) {
            return "La partida terminó.";
        }

        return "La partida terminó: " + reason;
    }

    public static String playerLeftMessage(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "Un jugador salió.";
        }
        return username.trim() + " salió.";
    }

    public static String playerLeftLobbyMessage(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "Un jugador salió del lobby.";
        }
        return username.trim() + " salió del lobby.";
    }

    public static String playerReconnectedMessage(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "El jugador volvió. La partida sigue.";
        }
        return username.trim() + " volvió. La partida sigue.";
    }

    public static String hostTransferredMessage(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "Ahora sos el host.";
        }
        return "Ahora el host es " + username.trim() + ".";
    }

    public static String singleplayerError(String reason) {
        if (reason == null) reason = "";
        reason = reason.trim();

        if ("NOT_LOGGED".equalsIgnoreCase(reason)) {
            return "Tenés que iniciar sesión para jugar.";
        }
        return "No se pudo continuar la partida singleplayer.";
    }
}