package com.busco.localizacao.infra.firebase;

import com.busco.localizacao.infra.firebase.FirebaseTrackingListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FirebaseMotoristaListener {

    private final FirebaseTrackingListener trackingListener;
    private final FirebaseApp firebaseApp;


    public FirebaseMotoristaListener(
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

                String viagemId =
                        viagemSnapshot.getKey();

                DatabaseReference motoristaRef =
                        viagemSnapshot
                                .getRef()
                                .child("motorista");

                motoristaRef.addChildEventListener(
                        new ChildEventListener() {

                            @Override
                            public void onChildAdded(
                                    DataSnapshot snapshot,
                                    String s
                            ) {

                                Map<String, Object> raw =
                                        (Map<String, Object>)
                                                snapshot.getValue();

                                trackingListener.onMotoristaEvent(
                                        viagemId,
                                        raw
                                );
                            }

                            @Override public void onChildChanged(DataSnapshot s, String p) {}
                            @Override public void onChildRemoved(DataSnapshot s) {}
                            @Override public void onChildMoved(DataSnapshot s, String p) {}
                            @Override public void onCancelled(DatabaseError e) {}
                        }
                );
            }

            @Override public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onChildRemoved(DataSnapshot snapshot) {}
            @Override public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}
            @Override public void onCancelled(DatabaseError error) {}
        });
    }
}