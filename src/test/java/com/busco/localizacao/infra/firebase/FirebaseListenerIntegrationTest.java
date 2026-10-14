package com.busco.localizacao.infra.firebase;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Teste de integração dos listeners do Firebase.
 *
 * Valida que o listener de {@code motorista/ultima} é registrado com
 * {@link ValueEventListener}, que dispara tanto em criação ({@code onChildAdded})
 * quanto em atualização ({@code onChildChanged}) do nó — o {@code busco-app}
 * grava com {@code set}, então as atualizações chegam por {@code onDataChange}.
 */
class FirebaseListenerIntegrationTest {

    private FirebaseTrackingListener trackingListener;
    private DatabaseReference viagensRef;
    private DatabaseReference viagemRef;
    private DatabaseReference motoristaRef;
    private DatabaseReference motoristaUltimaRef;

    @BeforeEach
    void setUp() {
        trackingListener = mock(FirebaseTrackingListener.class);

        viagensRef = mock(DatabaseReference.class);
        viagemRef = mock(DatabaseReference.class);
        motoristaRef = mock(DatabaseReference.class);
        motoristaUltimaRef = mock(DatabaseReference.class);

        // O listener navega a partir da referência da viagem:
        // viagens/{viagemId}/motorista/ultima
        when(viagemRef.child("motorista")).thenReturn(motoristaRef);
        when(motoristaRef.child("ultima")).thenReturn(motoristaUltimaRef);
    }

    private DataSnapshot viagemSnapshot(String viagemId) {
        DataSnapshot snapshot = mock(DataSnapshot.class);
        when(snapshot.getKey()).thenReturn(viagemId);
        when(snapshot.getRef()).thenReturn(viagemRef);
        return snapshot;
    }

    private DataSnapshot dadosSnapshot(Map<String, Object> payload) {
        DataSnapshot snapshot = mock(DataSnapshot.class);
        doReturn(payload).when(snapshot).getValue();
        return snapshot;
    }

    private Map<String, Object> payload(double lat, double lng) {
        Map<String, Object> raw = new HashMap<>();
        raw.put("latitude", lat);
        raw.put("longitude", lng);
        raw.put("timeStamp", "2026/10/14 12:00:00");
        return raw;
    }

    @Test
    void registraListenerDeMotoristaNoNoUltima() {
        FirebaseMotoristaListener listener =
                new FirebaseMotoristaListener(trackingListener, mock(FirebaseApp.class));

        listener.registrarListeners(viagensRef);

        ArgumentCaptor<ChildEventListener> childCaptor =
                ArgumentCaptor.forClass(ChildEventListener.class);
        verify(viagensRef).addChildEventListener(childCaptor.capture());

        childCaptor.getValue().onChildAdded(viagemSnapshot("viagem-1"), null);

        // O listener de valor é registrado exatamente em motorista/ultima
        verify(motoristaUltimaRef).addValueEventListener(any(ValueEventListener.class));
        assertEquals(1, listener.getListeners().size());
    }

    @Test
    void capturaCriacaoEAtualizacaoDoNoMotorista() {
        FirebaseMotoristaListener listener =
                new FirebaseMotoristaListener(trackingListener, mock(FirebaseApp.class));

        listener.registrarListeners(viagensRef);

        ArgumentCaptor<ChildEventListener> childCaptor =
                ArgumentCaptor.forClass(ChildEventListener.class);
        verify(viagensRef).addChildEventListener(childCaptor.capture());
        childCaptor.getValue().onChildAdded(viagemSnapshot("viagem-1"), null);

        ArgumentCaptor<ValueEventListener> valueCaptor =
                ArgumentCaptor.forClass(ValueEventListener.class);
        verify(motoristaUltimaRef).addValueEventListener(valueCaptor.capture());
        ValueEventListener valueListener = valueCaptor.getValue();

        // 1) criação do nó (onChildAdded equivalente)
        Map<String, Object> criacao = payload(-23.5505, -46.6333);
        valueListener.onDataChange(dadosSnapshot(criacao));
        verify(trackingListener).onMotoristaEvent("viagem-1", criacao);

        // 2) atualização do nó (onChildChanged equivalente, gravado com set)
        Map<String, Object> atualizacao = payload(-23.5510, -46.6340);
        valueListener.onDataChange(dadosSnapshot(atualizacao));
        verify(trackingListener).onMotoristaEvent("viagem-1", atualizacao);

        verify(trackingListener, times(2)).onMotoristaEvent(eq("viagem-1"), any());
    }

    @Test
    void ignoraPayloadNuloDoMotorista() {
        FirebaseMotoristaListener listener =
                new FirebaseMotoristaListener(trackingListener, mock(FirebaseApp.class));

        listener.registrarListeners(viagensRef);

        ArgumentCaptor<ChildEventListener> childCaptor =
                ArgumentCaptor.forClass(ChildEventListener.class);
        verify(viagensRef).addChildEventListener(childCaptor.capture());
        childCaptor.getValue().onChildAdded(viagemSnapshot("viagem-1"), null);

        ArgumentCaptor<ValueEventListener> valueCaptor =
                ArgumentCaptor.forClass(ValueEventListener.class);
        verify(motoristaUltimaRef).addValueEventListener(valueCaptor.capture());

        DataSnapshot vazio = mock(DataSnapshot.class);
        doReturn(null).when(vazio).getValue();
        valueCaptor.getValue().onDataChange(vazio);

        verify(trackingListener, never()).onMotoristaEvent(anyString(), any());
    }

    @Test
    void registraListenerDeAlunoNoNoUltima() {
        FirebaseAlunoListener listener =
                new FirebaseAlunoListener(trackingListener, mock(FirebaseApp.class));

        DatabaseReference alunosRef = mock(DatabaseReference.class);
        DatabaseReference alunoRef = mock(DatabaseReference.class);
        DatabaseReference alunoUltimaRef = mock(DatabaseReference.class);
        when(viagemRef.child("alunos")).thenReturn(alunosRef);
        when(alunoRef.child("ultima")).thenReturn(alunoUltimaRef);

        listener.registrarListeners(viagensRef);

        ArgumentCaptor<ChildEventListener> viagemCaptor =
                ArgumentCaptor.forClass(ChildEventListener.class);
        verify(viagensRef).addChildEventListener(viagemCaptor.capture());
        viagemCaptor.getValue().onChildAdded(viagemSnapshot("viagem-1"), null);

        ArgumentCaptor<ChildEventListener> alunosCaptor =
                ArgumentCaptor.forClass(ChildEventListener.class);
        verify(alunosRef).addChildEventListener(alunosCaptor.capture());

        DataSnapshot alunoSnapshot = mock(DataSnapshot.class);
        when(alunoSnapshot.getKey()).thenReturn("aluno-1");
        when(alunoSnapshot.getRef()).thenReturn(alunoRef);
        alunosCaptor.getValue().onChildAdded(alunoSnapshot, null);

        verify(alunoUltimaRef).addValueEventListener(any(ValueEventListener.class));
        assertEquals(1, listener.getListeners().size());
    }

    @Test
    void capturaCriacaoEAtualizacaoDoNoAluno() {
        FirebaseAlunoListener listener =
                new FirebaseAlunoListener(trackingListener, mock(FirebaseApp.class));

        DatabaseReference alunosRef = mock(DatabaseReference.class);
        DatabaseReference alunoRef = mock(DatabaseReference.class);
        DatabaseReference alunoUltimaRef = mock(DatabaseReference.class);
        when(viagemRef.child("alunos")).thenReturn(alunosRef);
        when(alunoRef.child("ultima")).thenReturn(alunoUltimaRef);

        listener.registrarListeners(viagensRef);

        ArgumentCaptor<ChildEventListener> viagemCaptor =
                ArgumentCaptor.forClass(ChildEventListener.class);
        verify(viagensRef).addChildEventListener(viagemCaptor.capture());
        viagemCaptor.getValue().onChildAdded(viagemSnapshot("viagem-1"), null);

        ArgumentCaptor<ChildEventListener> alunosCaptor =
                ArgumentCaptor.forClass(ChildEventListener.class);
        verify(alunosRef).addChildEventListener(alunosCaptor.capture());

        DataSnapshot alunoSnapshot = mock(DataSnapshot.class);
        when(alunoSnapshot.getKey()).thenReturn("aluno-1");
        when(alunoSnapshot.getRef()).thenReturn(alunoRef);
        alunosCaptor.getValue().onChildAdded(alunoSnapshot, null);

        ArgumentCaptor<ValueEventListener> valueCaptor =
                ArgumentCaptor.forClass(ValueEventListener.class);
        verify(alunoUltimaRef).addValueEventListener(valueCaptor.capture());
        ValueEventListener valueListener = valueCaptor.getValue();

        Map<String, Object> criacao = payload(-23.5505, -46.6333);
        valueListener.onDataChange(dadosSnapshot(criacao));
        verify(trackingListener).onAlunoEvent("viagem-1", "aluno-1", criacao);

        Map<String, Object> atualizacao = payload(-23.5512, -46.6350);
        valueListener.onDataChange(dadosSnapshot(atualizacao));
        verify(trackingListener).onAlunoEvent("viagem-1", "aluno-1", atualizacao);

        verify(trackingListener, times(2)).onAlunoEvent(eq("viagem-1"), eq("aluno-1"), any());
    }
}
