package com.frieren.dto;

import java.util.UUID;

public record StartCollaborativeSessionResponse(
        // El ID de la sesión, que es único.
        UUID sessionId,
        /*
         * La contraseña de 8 letras para unirse a la sesión.
         * La devuelve en formato ABC-23F
        */
        String passcode,

        // El link único que apunta al websocket, para que el usuario se conecte
        String websocketPath
) {
}
