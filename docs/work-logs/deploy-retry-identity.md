# Preserve deployment identity during retries

A connection interruption after a file-creation deploy was accepted caused the dispatcher to retry with a newer valid-after block. Re-signing changed the deploy ID and executed the creation twice. One complete encrypted record and one empty placeholder then prevented a safe remount.

Prepare and sign each queued operation once, retaining the same payload through retries. Before resubmitting, recognize an already included deploy and continue waiting for its finality. Bound finality lookup RPCs so a closed connection cannot outlive the polling deadline indefinitely. Preserve the existing convenience deployment API for callers.

Acceptance: the dispatcher prepares once across a failed attempt and successful retry, retains its durability barrier until success, and the unit suite passes. The incident's duplicate empty placeholder was repaired separately using an exact-match preflight; completed ciphertext was not rewritten.

Status: implemented. The complete unit suite (114 tests) and shaded JAR build passed with Java 17. Spotless formatting passed. The repaired live mount loaded successfully. A browser rehearsal created and deployed eight agents in a container, completed a two-round workflow and displayed its final response. Prior-inclusion checks run only on retries; checking before a first submission unnecessarily invokes the node’s missing-deploy wait. Backend automatic recovery has a separate, pre-existing registry-enumeration timeout and is not certified by this storage test.
