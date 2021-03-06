package org.corfudb.runtime.view; 

import lombok.extern.slf4j.Slf4j; 
import org.corfudb.protocols.logprotocol.IDivisibleEntry; 
import org.corfudb.protocols.logprotocol.StreamCOWEntry; 
import org.corfudb.protocols.logprotocol.TXEntry; 
import org.corfudb.protocols.wireprotocol.TokenResponse; 
import org.corfudb.runtime.CorfuRuntime; 
import org.corfudb.runtime.clients.SequencerClient; 
import org.corfudb.runtime.exceptions.OverwriteException; 
import org.corfudb.runtime.exceptions.ReplexOverwriteException; 

import java.util.Collections; 
import java.util.Set; 
import java.util.UUID; 
import java.util.function.Function; 

/**
 * Created by mwei on 12/11/15.
 */
@Slf4j
public  class  StreamsView {
	

    /**
     * The org.corfudb.runtime which backs this view.
     */
    CorfuRuntime runtime;
	

    public StreamsView(CorfuRuntime runtime) {
        this.runtime = runtime;
    }
	

    /**
     * Get a view on a stream. The view has its own pointer to the stream.
     *
     * @param stream The UUID of the stream to get a view on.
     * @return A view
     */
    public StreamView get(UUID stream) {
        // Todo(Maithem): we should have a mechanism for proper exclusion of a set of ids reserved by the system
        return new StreamView(runtime, stream);
    }
	

    /**
     * Make a copy-on-write copy of a stream.
     *
     * @param source      The UUID of the stream to make a copy of.
     * @param destination The UUID of the destination stream. It must not exist.
     * @return A view
     */
    public StreamView copy(UUID source, UUID destination, long timestamp) {
        boolean written = false;
        while (!written) {
            TokenResponse tokenResponse =
                    runtime.getSequencerView().nextToken(Collections.singleton(destination), 1);
            if (!tokenResponse.getBackpointerMap().get(destination).equals(-1L)) {
                try {
                    runtime.getAddressSpaceView().fillHole(tokenResponse.getToken());
                } catch (OverwriteException oe) {
                    log.trace("Attempted to hole fill due to already-existing stream but hole filled by other party");
                }
                throw new RuntimeException("Stream already exists!");
            }
            StreamCOWEntry entry = new StreamCOWEntry(source, timestamp);
            try {
                runtime.getAddressSpaceView().write(tokenResponse.getToken(), Collections.singleton(destination),
                        entry, tokenResponse.getBackpointerMap(), tokenResponse.getStreamAddresses());
                written = true;
            } catch (OverwriteException oe) {
                log.debug("hole fill during COW entry write, retrying...");
            }
        }
        return new StreamView(runtime, destination);
    }
	

    /**
     * Write an object to multiple streams, retuning the physical address it
     * was written at.
     * <p>
     * Note: While the completion of this operation guarantees that the write
     * has been persisted, it DOES NOT guarantee that the object has been
     * written to the stream. For example, another client may have deleted
     * the stream.
     *
     * @param object The object to write to the stream.
     * @return The address this
     */
    public long write(Set<UUID> streamIDs, Object object) {
        return acquireAndWrite(streamIDs, object, t -> true, t -> true);
    }
	

    public void writeAt(TokenResponse address, Set<UUID> streamIDs, Object object) throws OverwriteException {
        Function<UUID, Object> partialEntryFunction =
                object instanceof IDivisibleEntry ? ((IDivisibleEntry)object)::divideEntry : null;
        runtime.getAddressSpaceView().write(address.getToken(), streamIDs,
                object, address.getBackpointerMap(), address.getStreamAddresses(), partialEntryFunction);
    }
	

    /**
     * Write an object to multiple streams, retuning the physical address it
     * was written at.
     * <p>
     * Note: While the completion of this operation guarantees that the write
     * has been persisted, it DOES NOT guarantee that the object has been
     * written to the stream. For example, another client may have deleted
     * the stream.
     *
     * @param object The object to write to the stream.
     * @return The address this
     */
    public long acquireAndWrite(Set<UUID> streamIDs, Object object,
                                Function<TokenResponse, Boolean> acquisitionCallback,
                                Function<TokenResponse, Boolean> deacquisitionCallback) {
        boolean replexOverwrite = false;
        boolean overwrite = false;
        TokenResponse tokenResponse = null;
        while (true) {
            if (object instanceof TXEntry) {
                long token;
                if (overwrite) {
                    TokenResponse temp =
                            runtime.getSequencerView().nextToken(streamIDs, 1, true, false, true, ((TXEntry) object).getReadTimestamp());
                    token = temp.getToken();
                    tokenResponse = new TokenResponse(token, temp.getBackpointerMap(), tokenResponse.getStreamAddresses());
                } else {
                    log.trace("object is instance of TXEntry! readTimestamp: {}", ((TXEntry) object).getReadTimestamp());
                    tokenResponse =
                            runtime.getSequencerView().nextToken(streamIDs, 1, false, false, true, ((TXEntry) object).getReadTimestamp());
                    token = tokenResponse.getToken();
                }
                log.trace("Write[{}]: acquired token = {}, global addr: {}", streamIDs, tokenResponse, token);
                if (acquisitionCallback != null) {
                    if (!acquisitionCallback.apply(tokenResponse)) {
                        log.trace("Acquisition rejected token, hole filling acquired address.");
                        try {
                            runtime.getAddressSpaceView().fillHole(token);
                        } catch (OverwriteException oe) {
                            log.trace("Hole fill completed by remote client.");
                        }
                        return -1L;
                    }
                }
                if (token == -1L) {
                    if (deacquisitionCallback != null && !deacquisitionCallback.apply(tokenResponse)) {
                        log.trace("Acquisition rejected overwrite at {}, not retrying.", token);
                    }
                    return -1L;
                }
                try {
                    runtime.getAddressSpaceView().write(token, streamIDs,
                            object, tokenResponse.getBackpointerMap(), tokenResponse.getStreamAddresses());
                    return token;
                } catch (ReplexOverwriteException re) {
<<<<<<< C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var1_4908609521617044504.java
                    //((TXEntry) object).setAborted(true);
=======
>>>>>>> C:\Users\GUILHE~1\AppData\Local\Temp\fstmerge_var2_5521236003319044946.java
                    if (deacquisitionCallback != null && !deacquisitionCallback.apply(tokenResponse)) {
                        log.trace("Acquisition rejected overwrite at {}, not retrying.", token);
                        return -1L;
                    }
                    //
                    return tokenResponse.getToken(); // uh, probably should figure out what is going on here.
                } catch (OverwriteException oe) {
                    if (deacquisitionCallback != null && !deacquisitionCallback.apply(tokenResponse)) {
                        log.trace("Acquisition rejected overwrite at {}, not retrying.", token);
                        return -1L;
                    }
                    replexOverwrite = false;
                    overwrite = true;
                    log.debug("Overwrite occurred at {}, retrying.", token);
                }
            } else {
                long token;
                if (replexOverwrite) {
                    tokenResponse =
                            runtime.getSequencerView().nextToken(streamIDs, 1, false, true);
                    token = tokenResponse.getToken();
                } else if (overwrite) {
                    TokenResponse temp =
                            runtime.getSequencerView().nextToken(streamIDs, 1, true, false);
                    token = temp.getToken();
                    tokenResponse = new TokenResponse(token, temp.getBackpointerMap(), tokenResponse.getStreamAddresses());
                } else {
                    tokenResponse =
                            runtime.getSequencerView().nextToken(streamIDs, 1);
                    token = tokenResponse.getToken();
                }
                log.trace("Write[{}]: acquired token = {}, global addr: {}", streamIDs, tokenResponse, token);
                if (acquisitionCallback != null) {
                    if (!acquisitionCallback.apply(tokenResponse)) {
                        log.trace("Acquisition rejected token, hole filling acquired address.");
                        try {
                            runtime.getAddressSpaceView().fillHole(token);
                        } catch (OverwriteException oe) {
                            log.trace("Hole fill completed by remote client.");
                        }
                        return -1L;
                    }
                }
                try {
                    Function<UUID, Object> partialEntryFunction =
                            object instanceof IDivisibleEntry ? ((IDivisibleEntry)object)::divideEntry : null;
                    runtime.getAddressSpaceView().write(token, streamIDs,
                            object, tokenResponse.getBackpointerMap(), tokenResponse.getStreamAddresses(), partialEntryFunction);
                    return token;
                } catch (ReplexOverwriteException re) {
                    if (deacquisitionCallback != null && !deacquisitionCallback.apply(tokenResponse)) {
                        log.trace("Acquisition rejected overwrite at {}, not retrying.", token);
                        return -1L;
                    }
                    replexOverwrite = true;
                    overwrite = false;
                } catch (OverwriteException oe) {
                    if (deacquisitionCallback != null && !deacquisitionCallback.apply(tokenResponse)) {
                        log.trace("Acquisition rejected overwrite at {}, not retrying.", token);
                        return -1L;
                    }
                    replexOverwrite = false;
                    overwrite = true;
                    log.debug("Overwrite occurred at {}, retrying.", token);
                }
            }
        }
    }

}

