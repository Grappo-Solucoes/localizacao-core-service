package com.busco.localizacao.infra.firebase;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FirebaseMotoristaListenerTest {

    private FirebaseTrackingListener trackingListener;
    private FirebaseApp firebaseApp;
    private FirebaseDatabase database;
    private DatabaseReference viagensRef;
    private DatabaseReference viagemRef;
    private DatabaseReference motoristaRef;
    private DatabaseReference ultimaRef;

    @BeforeEach
    void setUp() {
        trackingListener = mock(FirebaseTrackingListener.class);
        firebaseApp = mock(FirebaseApp.class);
        database = mock(FirebaseDatabase.class);
        viagensRef = mock(DatabaseReference.class);
        viagemRef = mock(DatabaseReference.class);
        motoristaRef = mock(DatabaseReference.class);
        ultimaRef = mock(DatabaseReference.class);

        when(database.getReference("viagens")).thenReturn(viagensRef);
        when(viagemRef.child("motorista")).thenReturn(motoristaRef);
        when(motoristaRef.child("ultima")).thenReturn(ultimaRef);
    }

    private ChildEventListener initAndCaptureChildListener() {
        try (MockedStatic<FirebaseDatabase> mocked =
                     mockStatic(FirebaseDatabase.class)) {
            mocked.when(() -> FirebaseDatabase.getInstance(firebaseApp)).thenReturn(database);

            new FirebaseMotoristaListener(trackingListener, firebaseApp).init();

            ArgumentCaptor<ChildEventListener> captor =
                    ArgumentCaptor.forClass(ChildEventListener.class);
            verify(viagensRef).addChildEventListener(captor.capture());
            return captor.getValue();
        }
    }

    @Test
    void init_registraListenerNoNoViagens() {
        initAndCaptureChildListener();
        verify(database).getReference("viagens");
    }

    @Test
    void onChildAdded_registraValueListenerEmMotoristaUltima() {
        ChildEventListener childListener = initAndCaptureChildListener();

        DataSnapshot viagemSnapshot = mock(DataSnapshot.class);
        when(viagemSnapshot.getKey()).thenReturn("viagem-1");
        when(viagemSnapshot.getRef()).thenReturn(viagemRef);

        childListener.onChildAdded(viagemSnapshot, null);

        verify(viagemRef).child("motorista");
        verify(motoristaRef).child("ultima");
        verify(ultimaRef).addValueEventListener(any(ValueEventListener.class));
    }

    @Test
    void onDataChangeDispara_capturaOnChildAddedEOnChildChanged() {
        ChildEventListener childListener = initAndCaptureChildListener();

        DataSnapshot viagemSnapshot = mock(DataSnapshot.class);
        when(viagemSnapshot.getKey()).thenReturn("viagem-1");
        when(viagemSnapshot.getRef()).thenReturn(viagemRef);
        childListener.onChildAdded(viagemSnapshot, null);

        ArgumentCaptor<ValueEventListener> captor =
                ArgumentCaptor.forClass(ValueEventListener.class);
        verify(ultimaRef).addValueEventListener(captor.capture());
        ValueEventListener valueListener = captor.getValue();

        Map<String, Object> raw = new HashMap<>();
        raw.put("latitude", -23.5505);
        raw.put("longitude", -46.6333);
        raw.put("timeStamp", "2026/10/14 10:30:00");

        DataSnapshot dados = mock(DataSnapshot.class);
        when(dados.getValue()).thenReturn(raw);

        // simula onChildAdded e depois onChildChanged no RTDB: ambos chegam como onDataChange
        valueListener.onDataChange(dados);
        valueListener.onDataChange(dados);

        verify(trackingListener, times(2)).onMotoristaEvent(eq("viagem-1"), any());
    }

    @Test
    void onDataChangeComSnapshotNulo_naoPublicaEvento() {
        ChildEventListener childListener = initAndCaptureChildListener();

        DataSnapshot viagemSnapshot = mock(DataSnapshot.class);
        when(viagemSnapshot.getKey()).thenReturn("viagem-1");
        when(viagemSnapshot.getRef()).thenReturn(viagemRef);
        childListener.onChildAdded(viagemSnapshot, null);

        ArgumentCaptor<ValueEventListener> captor =
                ArgumentCaptor.forClass(ValueEventListener.class);
        verify(ultimaRef).addValueEventListener(captor.capture());

        DataSnapshot dados = mock(DataSnapshot.class);
        when(dados.getValue()).thenReturn(null);

        captor.getValue().onDataChange(dados);

        verifyNoInteractions(trackingListener);
    }

    @Test
    void onChildAddedIgnoraViagemSemKey() {
        ChildEventListener childListener = initAndCaptureChildListener();

        DataSnapshot viagemSnapshot = mock(DataSnapshot.class);
        when(viagemSnapshot.getKey()).thenReturn(null);

        childListener.onChildAdded(viagemSnapshot, null);

        verifyNoInteractions(ultimaRef);
        verifyNoInteractions(trackingListener);
    }

    @Test
    void leituraUsaNoContratualMotoristaUltima() {
        ChildEventListener childListener = initAndCaptureChildListener();

        DataSnapshot viagemSnapshot = mock(DataSnapshot.class);
        when(viagemSnapshot.getKey()).thenReturn("viagem-42");
        when(viagemSnapshot.getRef()).thenReturn(viagemRef);

        childListener.onChildAdded(viagemSnapshot, null);

        // contrato RTDB: viagens/{id}/motorista/ultima
        verify(viagemRef).child("motorista");
        verify(motoristaRef).child("ultima");
        assertEquals("viagem-42", viagemSnapshot.getKey());
    }
}
