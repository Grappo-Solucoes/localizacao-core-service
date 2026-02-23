package com.busco.localizacao_core_service.infra.firebase;

import com.google.firebase.database.*;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FirebaseMotoristaListener {

    public FirebaseMotoristaListener(
            FirebaseTrackingListener trackingListener
    ) {

        DatabaseReference ref =
                FirebaseDatabase
                        .getInstance()
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
                                        snapshot.getKey(),
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