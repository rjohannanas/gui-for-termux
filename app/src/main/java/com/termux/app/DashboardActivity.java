package com.termux.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.termux.R;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private TermuxService mTermuxService;
    private boolean mIsBound = false;

    // Conexión con el servicio de Termux en segundo plano
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            TermuxService.LocalBinder binder = (TermuxService.LocalBinder) service;
            mTermuxService = binder.service;
            mIsBound = true;
            // Ya no intentamos crear la sesión aquí para evitar errores de compatibilidad.
            // Lo manejaremos en los botones.
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mTermuxService = null;
            mIsBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        // 1. Iniciamos el servicio de Termux explícitamente
        Intent serviceIntent = new Intent(this, TermuxService.class);
        startService(serviceIntent);

        // 2. Nos vinculamos (bind) al servicio
        bindService(serviceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        setupButtons();
    }

    private void setupButtons() {
        // Botón 1: Verificar Node
        findViewById(R.id.btn_install_node).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Comando que verifica si existe node, si no, lo instala
                enviarComando("if [ ! -f \"$PREFIX/bin/node\" ]; then pkg update -y && pkg install nodejs -y; else echo 'Node.js ya instalado.'; fi && node -v");
            }
        });

        // Botón 2: Script de prueba
        findViewById(R.id.btn_run_script).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Crea un script y lo ejecuta
                enviarComando("echo 'console.log(\"¡Hola desde Java Dashboard!\")' > test.js && node test.js");
            }
        });

        // Botón 3: Ir al terminal clásico
        findViewById(R.id.btn_open_terminal).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                abrirTermuxActivity();
            }
        });
    }

    private void abrirTermuxActivity() {
        Intent intent = new Intent(DashboardActivity.this, TermuxActivity.class);
        startActivity(intent);
    }

    private void enviarComando(String cmd) {
        if (!mIsBound || mTermuxService == null) {
            Toast.makeText(this, "Servicio no conectado. Espere un momento.", Toast.LENGTH_SHORT).show();
            return;
        }

        // VERIFICACIÓN INTELIGENTE:
        // Si no hay sesiones activas, abrimos TermuxActivity para que se inicialice.
        if (mTermuxService.isTermuxSessionsEmpty()) {
            Toast.makeText(this, "Inicializando sistema... Por favor espere.", Toast.LENGTH_LONG).show();
            abrirTermuxActivity();
            return;
        }

        List<TermuxSession> sessions = mTermuxService.getTermuxSessions();

        if (!sessions.isEmpty()) {
            // Tomamos la primera sesión disponible
            TermuxSession termuxSession = sessions.get(0);
            TerminalSession terminalSession = termuxSession.getTerminalSession();

            if (terminalSession != null) {
                String comandoFinal = cmd + "\n";
                terminalSession.write(comandoFinal);
                Toast.makeText(this, "Comando enviado. Verifique en Consola.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mIsBound) {
            unbindService(mServiceConnection);
            mIsBound = false;
        }
    }
}
