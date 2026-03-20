package com.alongiss.Alonias;

public final class ClientMessageUtils {

    private ClientMessageUtils() {}

    public static String authMessage(String code, String reason) {
        reason = safe(reason);

        if ("ALREADY_CONNECTED".equalsIgnoreCase(reason)) {
            return "That username is already connected.";
        }
        if ("BAD_CREDENTIALS".equalsIgnoreCase(reason)) {
            return "Incorrect username or password.";
        }
        if ("USER_EXISTS".equalsIgnoreCase(reason)) {
            return "That username already exists.";
        }
        if ("BAD_FORMAT".equalsIgnoreCase(reason)) {
            return "Invalid login data.";
        }
        if ("NOT_LOGGED".equalsIgnoreCase(reason)) {
            return "You need to log in first.";
        }

        return "reg".equalsIgnoreCase(code)
                ? "Could not create the account."
                : "Could not log in.";
    }

    public static String authSuccessMessage(String code) {
        return "reg".equalsIgnoreCase(code)
                ? "Account created successfully."
                : "Login successful.";
    }

    public static String roomCreateMessage(String reason) {
        reason = safe(reason);

        if ("NOT_LOGGED".equalsIgnoreCase(reason)) {
            return "You need to log in to create a room.";
        }
        if ("BAD_FORMAT".equalsIgnoreCase(reason)) {
            return "The room data is invalid.";
        }
        return "Could not create the room.";
    }

    public static String roomCreateSuccessMessage() {
        return "Room created successfully.";
    }

    public static String roomJoinMessage(String reason) {
        reason = safe(reason);

        if ("NO_ROOM".equalsIgnoreCase(reason)) {
            return "That room no longer exists.";
        }
        if ("ROOM_NOT_WAITING".equalsIgnoreCase(reason)) {
            return "That match has already started.";
        }
        if ("BAD_PASSWORD".equalsIgnoreCase(reason)) {
            return "Incorrect room password.";
        }
        if ("ROOM_FULL".equalsIgnoreCase(reason)) {
            return "That room is full.";
        }
        if ("USERNAME_ALREADY_IN_ROOM".equalsIgnoreCase(reason)) {
            return "That username is already in this room.";
        }
        if ("ALREADY_IN_OTHER_ROOM".equalsIgnoreCase(reason)) {
            return "That username is already in another room.";
        }
        if ("NO_USER".equalsIgnoreCase(reason)) {
            return "That username is not valid.";
        }
        if ("NOT_LOGGED".equalsIgnoreCase(reason)) {
            return "You need to log in first.";
        }
        if ("BAD_FORMAT".equalsIgnoreCase(reason)) {
            return "Could not process the join request.";
        }

        return "Could not join the room.";
    }

    public static String roomJoinSuccessMessage() {
        return "Joined room successfully.";
    }

    public static String roomLeaveMessage(boolean ok) {
        return ok ? "You left the room." : "Could not leave the room.";
    }

    public static String lobbyStartMessage(String reason) {
        reason = safe(reason);

        if ("NO_ROOM".equalsIgnoreCase(reason)) {
            return "That room no longer exists.";
        }
        if ("NOT_WAITING".equalsIgnoreCase(reason)) {
            return "The room is no longer waiting for players.";
        }
        if ("NEED_2_PLAYERS".equalsIgnoreCase(reason)) {
            return "At least 2 players are needed to start.";
        }
        if ("NOT_HOST".equalsIgnoreCase(reason)) {
            return "Only the host can start the match.";
        }
        if ("NO_TURN_PLAYERS".equalsIgnoreCase(reason)) {
            return "There are not enough players to assign turns.";
        }
        if ("NO_GAME_MANAGER".equalsIgnoreCase(reason)) {
            return "The server is not ready to start the match yet.";
        }

        return "Could not start the match.";
    }

    public static String lobbyStartSuccessMessage() {
        return "Match started.";
    }

    public static String skipMessage(boolean ok, String reason) {
        if (ok) {
            return "Word skipped.";
        }

        reason = safe(reason);

        if ("NO_GAME".equalsIgnoreCase(reason)) {
            return "The match is no longer active.";
        }
        if ("PAUSED".equalsIgnoreCase(reason)) {
            return "The match is currently paused.";
        }
        if ("NOT_EXPLAINER".equalsIgnoreCase(reason)) {
            return "Only the explainer can skip the word.";
        }
        if ("NO_GAME_MANAGER".equalsIgnoreCase(reason)) {
            return "The server could not process the skip.";
        }

        return "Could not skip the word.";
    }

    public static String guessMessage(boolean correct) {
        return correct ? "Correct!" : "That was not the word. Try again.";
    }

    public static String genericServerError(String reason) {
        reason = safe(reason);

        if ("NOT_LOGGED".equalsIgnoreCase(reason)) {
            return "Your session is not active.";
        }
        if ("BAD_FORMAT".equalsIgnoreCase(reason)) {
            return "Invalid data was sent to the server.";
        }
        if ("UNKNOWN".equalsIgnoreCase(reason)) {
            return "The server did not understand the request.";
        }
        if ("BAD".equalsIgnoreCase(reason)) {
            return "The request sent to the server was invalid.";
        }
        if ("NO_GAME".equalsIgnoreCase(reason)) {
            return "The match is no longer available.";
        }
        if ("PAUSED".equalsIgnoreCase(reason)) {
            return "The match is paused right now.";
        }

        return "A communication error occurred with the server.";
    }

    public static String gameEndedMessage(String reason) {
        reason = safe(reason);

        if ("NOT_ENOUGH_PLAYERS".equalsIgnoreCase(reason)) {
            return "The match ended because there were not enough players left.";
        }
        if ("PLAYER_LEFT".equalsIgnoreCase(reason)) {
            return "The match ended because a player left.";
        }
        if ("GAME_OVER".equalsIgnoreCase(reason)) {
            return "The match is over.";
        }

        return "The match ended: " + reason;
    }

    public static String playerLeftMessage(String username) {
        username = safe(username);
        if (username.isEmpty()) {
            return "A player left.";
        }
        return username + " left the match.";
    }

    public static String playerLeftLobbyMessage(String username) {
        username = safe(username);
        if (username.isEmpty()) {
            return "A player left the lobby.";
        }
        return username + " left the lobby.";
    }

    public static String playerDisconnectedMessage(String username) {
        username = safe(username);
        if (username.isEmpty()) {
            return "A player disconnected. Waiting for reconnection.";
        }
        return username + " disconnected. Waiting for reconnection.";
    }

    public static String playerReconnectedMessage(String username) {
        username = safe(username);
        if (username.isEmpty()) {
            return "The player reconnected. The match will continue.";
        }
        return username + " reconnected. The match will continue.";
    }

    public static String gamePausedMessage() {
        return "The match is paused.";
    }

    public static String gameResumedMessage() {
        return "The match resumed.";
    }

    public static String hostTransferredMessage(String username) {
        username = safe(username);
        if (username.isEmpty()) {
            return "You are now the host.";
        }
        return username + " is now the host.";
    }

    public static String singleplayerError(String reason) {
        reason = safe(reason);

        if ("NOT_LOGGED".equalsIgnoreCase(reason)) {
            return "You need to log in to play.";
        }
        return "Could not continue the single-player match.";
    }

    public static String singleplayerStartedMessage() {
        return "Single-player match started.";
    }

    public static String voiceChatUnavailableMessage() {
        return "Voice chat is currently unavailable.";
    }

    public static String voiceChatMutedMessage(boolean muted) {
        return muted ? "Microphone muted." : "Microphone unmuted.";
    }

    public static String connectionLostMessage() {
        return "Connection lost. Please try again.";
    }

    public static String unexpectedResponseMessage() {
        return "Unexpected response from the server.";
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}