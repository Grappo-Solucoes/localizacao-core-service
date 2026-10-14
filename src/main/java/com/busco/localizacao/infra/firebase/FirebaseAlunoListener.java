package com.busco.localizacao.infra.firebase;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class FirebaseAlunoListener {

    private final FirebaseTrackingListener trackingListener;
    private final FirebaseApp firebaseApp;

    private final Map<String, ValueEventListener> listeners = new ConcurrentHashMap<>();
    private static final int MAX_LISTENERS = 1000;
    private static final long REFRESH_INTERVAL_MINUTES = 5;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public FirebaseAlunoListener(
            FirebaseTrackingListener trackingListener, FirebaseApp firebaseApp
    ) {
        this.trackingListener = trackingListener;
        this.firebaseApp = firebaseApp;
    }

    @PostConstruct
    public void init() {

        DatabaseReference ref =
                FirebaseDatabase.getInstance(firebaseApp)
                        .getReference("viagens");

        registrarListeners(ref);

        scheduler.scheduleAtFixedRate(() -> {
            listeners.clear();
        }, REFRESH_INTERVAL_MINUTES, REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Registra os listeners de aluno a partir do nó raiz {@code viagens}.
     * Usa {@link ValueEventListener} sobre {@code alunos/{alunoId}/ultima}, que dispara
     * tanto em criação ({@code onChildAdded}) quanto em atualização ({@code onChildChanged}),
     * pois o {@code busco-app} grava o nó com {@code set} (não {@code push}).
     */
    void registrarListeners(DatabaseReference ref) {

        ref.addChildEventListener(new ChildEventListener() {

            @Override
            public void onChildAdded(
                    DataSnapshot viagemSnapshot,
                    String previousChildName
            ) {
                String viagemId = viagemSnapshot.getKey();
                if (viagemId == null) return;

                DatabaseReference alunosRef =
                        viagemSnapshot.getRef().child("alunos");

                alunosRef.addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(
                            DataSnapshot alunoSnapshot,
                            String s
                    ) {
                        String alunoId = alunoSnapshot.getKey();
                        if (alunoId == null) return;
                        if (listeners.size() >= MAX_LISTENERS) return;

                        DatabaseReference alunoUltimaRef =
                                alunoSnapshot.getRef().child("ultima");

                        String listenerKey = viagemId + ":" + alunoId;
                        ValueEventListener listener = new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot snapshot) {
                                Map<String, Object> raw = (Map<String, Object>) snapshot.getValue();
                                if (raw == null) return;
                                trackingListener.onAlunoEvent(viagemId, alunoId, raw);
                            }

                            @Override
                            public void onCancelled(DatabaseError error) {
                                listeners.remove(listenerKey);
                            }
                        };

                        alunoUltimaRef.addValueEventListener(listener);
                        listeners.put(listenerKey, listener);
                    }

                    @Override
                    public void onChildChanged(DataSnapshot s, String p) {}
                    @Override
                    public void onChildRemoved(DataSnapshot s) {}
                    @Override
                    public void onChildMoved(DataSnapshot s, String p) {}
                    @Override
                    public void onCancelled(DatabaseError e) {}
                });
            }

            @Override
            public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override
            public void onChildRemoved(DataSnapshot snapshot) {}
            @Override
            public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override
            public void onCancelled(DatabaseError error) {}
        });
    }

    Map<String, ValueEventListener> getListeners() {
        return listeners;
    }
}
