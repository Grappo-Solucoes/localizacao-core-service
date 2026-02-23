package com.busco.localizacao.infra.firebase;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FirebaseAlunoListener {

    private final FirebaseTrackingListener trackingListener;
    private final FirebaseApp firebaseApp;

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

        ref.addChildEventListener(new ChildEventListener() {

            @Override
            public void onChildAdded(
                    DataSnapshot viagemSnapshot,
                    String previousChildName
            ) {

                String viagemId = viagemSnapshot.getKey();

                DatabaseReference alunosRef =
                        viagemSnapshot.getRef().child("alunos");

                alunosRef.addChildEventListener(
                        new ChildEventListener() {

                            @Override
                            public void onChildAdded(
                                    DataSnapshot alunoSnapshot,
                                    String s
                            ) {

                                String alunoId =
                                        alunoSnapshot.getKey();

                                alunoSnapshot.getRef()
                                        .addChildEventListener(
                                                new ChildEventListener() {

                                                    @Override
                                                    public void onChildAdded(
                                                            DataSnapshot eventSnapshot,
                                                            String s
                                                    ) {

                                                        Map<String, Object> raw =
                                                                (Map<String, Object>)
                                                                        eventSnapshot.getValue();

                                                        trackingListener.onAlunoEvent(
                                                                viagemId,
                                                                alunoId,
                                                                raw
                                                        );
                                                    }

                                                    @Override
                                                    public void onChildChanged(DataSnapshot s, String p) {
                                                    }

                                                    @Override
                                                    public void onChildRemoved(DataSnapshot s) {
                                                    }

                                                    @Override
                                                    public void onChildMoved(DataSnapshot s, String p) {
                                                    }

                                                    @Override
                                                    public void onCancelled(DatabaseError e) {
                                                    }
                                                }
                                        );
                            }

                            @Override
                            public void onChildChanged(DataSnapshot s, String p) {
                            }

                            @Override
                            public void onChildRemoved(DataSnapshot s) {
                            }

                            @Override
                            public void onChildMoved(DataSnapshot s, String p) {
                            }

                            @Override
                            public void onCancelled(DatabaseError e) {
                            }
                        }
                );
            }

            @Override
            public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
            }

            @Override
            public void onChildRemoved(DataSnapshot snapshot) {
            }

            @Override
            public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
            }

            @Override
            public void onCancelled(DatabaseError error) {
            }
        });
    }
}