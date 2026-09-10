package io.f1r3fly.f1r3drive.blockchain.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.f1r3fly.f1r3drive.errors.EmptyExploratoryResult;
import io.f1r3fly.f1r3drive.errors.F1r3DriveError;
import java.util.List;
import org.junit.jupiter.api.Test;
import rhoapi.RhoTypes;

class ExploratoryResultTest {
  private final RhoTypes.Expr value = RhoTypes.Expr.newBuilder().setGString("record").build();
  private final RhoTypes.Par record = RhoTypes.Par.newBuilder().addExprs(value).build();

  @Test
  void noRecordsAreExplicitlyAbsent() {
    assertThrows(
        EmptyExploratoryResult.class,
        () -> F1r3flyBlockchainClient.requireSingleExpression(List.of()));
    assertThrows(
        EmptyExploratoryResult.class,
        () ->
            F1r3flyBlockchainClient.requireSingleExpression(
                List.of(RhoTypes.Par.getDefaultInstance())));
  }

  @Test
  void allResultContainersAreInspectedBeforeDeclaringAbsence() {
    assertEquals(
        value,
        F1r3flyBlockchainClient.requireSingleExpression(
            List.of(RhoTypes.Par.getDefaultInstance(), record)));
  }

  @Test
  void duplicateRecordsCannotSilentlyHideExistingFiles() {
    assertThrows(
        F1r3DriveError.class,
        () -> F1r3flyBlockchainClient.requireSingleExpression(List.of(record, record)));
    assertThrows(
        F1r3DriveError.class,
        () ->
            F1r3flyBlockchainClient.requireSingleExpression(
                List.of(RhoTypes.Par.newBuilder().addExprs(value).addExprs(value).build())));
  }
}
