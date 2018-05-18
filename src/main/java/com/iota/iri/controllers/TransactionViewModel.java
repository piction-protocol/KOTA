package com.iota.iri.controllers;

import java.util.*;

import com.iota.iri.model.*;
import com.iota.iri.storage.Indexable;
import com.iota.iri.storage.Persistable;
import com.iota.iri.storage.Tangle;
import com.iota.iri.utils.Converter;
import com.iota.iri.utils.Pair;

public class TransactionViewModel {

    private final com.iota.iri.model.Transaction transaction;

    public static final int SIZE = 1604;
    private static final int TAG_SIZE_IN_BYTES = 17; // = ceil(81 TRITS / 5 TRITS_PER_BYTE)

    public static final long SUPPLY = 2779530283277761L; // = (3^33 - 1) / 2

    public static final int SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET = 0, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE = 6561;
    public static final int ADDRESS_TRINARY_OFFSET = SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET + SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE, ADDRESS_TRINARY_SIZE = 243;
    public static final int VALUE_TRINARY_OFFSET = ADDRESS_TRINARY_OFFSET + ADDRESS_TRINARY_SIZE, VALUE_TRINARY_SIZE = 81, VALUE_USABLE_TRINARY_SIZE = 33;
    public static final int OBSOLETE_TAG_TRINARY_OFFSET = VALUE_TRINARY_OFFSET + VALUE_TRINARY_SIZE, OBSOLETE_TAG_TRINARY_SIZE = 81;
    public static final int TIMESTAMP_TRINARY_OFFSET = OBSOLETE_TAG_TRINARY_OFFSET + OBSOLETE_TAG_TRINARY_SIZE, TIMESTAMP_TRINARY_SIZE = 27;
    public static final int CURRENT_INDEX_TRINARY_OFFSET = TIMESTAMP_TRINARY_OFFSET + TIMESTAMP_TRINARY_SIZE, CURRENT_INDEX_TRINARY_SIZE = 27;
    public static final int LAST_INDEX_TRINARY_OFFSET = CURRENT_INDEX_TRINARY_OFFSET + CURRENT_INDEX_TRINARY_SIZE, LAST_INDEX_TRINARY_SIZE = 27;
    public static final int BUNDLE_TRINARY_OFFSET = LAST_INDEX_TRINARY_OFFSET + LAST_INDEX_TRINARY_SIZE, BUNDLE_TRINARY_SIZE = 243;
    public static final int TRUNK_TRANSACTION_TRINARY_OFFSET = BUNDLE_TRINARY_OFFSET + BUNDLE_TRINARY_SIZE, TRUNK_TRANSACTION_TRINARY_SIZE = 243;
    public static final int BRANCH_TRANSACTION_TRINARY_OFFSET = TRUNK_TRANSACTION_TRINARY_OFFSET + TRUNK_TRANSACTION_TRINARY_SIZE, BRANCH_TRANSACTION_TRINARY_SIZE = 243;

    public static final int TAG_TRINARY_OFFSET = BRANCH_TRANSACTION_TRINARY_OFFSET + BRANCH_TRANSACTION_TRINARY_SIZE, TAG_TRINARY_SIZE = 81;
    public static final int ATTACHMENT_TIMESTAMP_TRINARY_OFFSET = TAG_TRINARY_OFFSET + TAG_TRINARY_SIZE, ATTACHMENT_TIMESTAMP_TRINARY_SIZE = 27;
    public static final int ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_OFFSET = ATTACHMENT_TIMESTAMP_TRINARY_OFFSET + ATTACHMENT_TIMESTAMP_TRINARY_SIZE, ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_SIZE = 27;
    public static final int ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_OFFSET = ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_OFFSET + ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_SIZE, ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_SIZE = 27;
    private static final int NONCE_TRINARY_OFFSET = ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_OFFSET + ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_SIZE, NONCE_TRINARY_SIZE = 81;

    public static final int TRINARY_SIZE = NONCE_TRINARY_OFFSET + NONCE_TRINARY_SIZE;

    public static final int ESSENCE_TRINARY_OFFSET = ADDRESS_TRINARY_OFFSET, ESSENCE_TRINARY_SIZE = ADDRESS_TRINARY_SIZE + VALUE_TRINARY_SIZE + OBSOLETE_TAG_TRINARY_SIZE + TIMESTAMP_TRINARY_SIZE + CURRENT_INDEX_TRINARY_SIZE + LAST_INDEX_TRINARY_SIZE;


    private AddressViewModel address;
    private ApproveeViewModel approovers;
    private TransactionViewModel trunk;
    private TransactionViewModel branch;
    private final Hash hash;


    public final static int GROUP = 0; // transactions GROUP means that's it's a non-leaf node (leafs store transaction value)
    public final static int PREFILLED_SLOT = 1; // means that we know only hash of the tx, the rest is unknown yet: only another tx references that hash
    public final static int FILLED_SLOT = -1; //  knows the hash only coz another tx references that hash

    private int[] trits;
    public int weightMagnitude;

    public static void fillMetadata(Tangle tangle, TransactionViewModel transactionViewModel) throws Exception {
        if (Hash.NULL_HASH.equals(transactionViewModel.getHash())) {
            return;
        }
        if(transactionViewModel.getType() == FILLED_SLOT && !transactionViewModel.transaction.getParsed()) {
            tangle.saveBatch(transactionViewModel.getMetadataSaveBatch());
        }
    }

    public static TransactionViewModel find(Tangle tangle, byte[] hash) throws Exception {
        TransactionViewModel transactionViewModel = new TransactionViewModel((Transaction) tangle.find(Transaction.class, hash), new Hash(hash));
        fillMetadata(tangle, transactionViewModel);
        return transactionViewModel;
    }

    public static TransactionViewModel fromHash(Tangle tangle, final Hash hash) throws Exception {
        TransactionViewModel transactionViewModel = new TransactionViewModel((Transaction) tangle.load(Transaction.class, hash), hash);
        fillMetadata(tangle, transactionViewModel);
        return transactionViewModel;
    }

    public static boolean mightExist(Tangle tangle, Hash hash) throws Exception {
        return tangle.maybeHas(Transaction.class, hash);
    }

    public TransactionViewModel(final Transaction transaction, final Hash hash) {
        this.transaction = transaction == null || transaction.getBytes() == null ? new Transaction(): transaction;
        this.hash = hash == null? Hash.NULL_HASH: hash;
        weightMagnitude = this.hash.trailingZeros();
    }

    public TransactionViewModel(final int[] trits, Hash hash) {
        transaction = new com.iota.iri.model.Transaction();
        this.trits = new int[trits.length];
        System.arraycopy(trits, 0, this.trits, 0, trits.length);
        transaction.setBytes(Converter.allocateBytesForTrits(trits.length));
        Converter.bytes(trits, 0, transaction.getBytes(), 0, trits.length);
        this.hash = hash;

        transaction.setType(FILLED_SLOT);

        weightMagnitude = this.hash.trailingZeros();
        transaction.setValidity(0);
        transaction.setArrivalTime(0);
    }


    public TransactionViewModel(final byte[] bytes, Hash hash) throws RuntimeException {
        transaction = new Transaction();
        transaction.setBytes(new byte[SIZE]);
        System.arraycopy(bytes, 0, transaction.getBytes(), 0, SIZE);
        this.hash = hash;
        weightMagnitude = this.hash.trailingZeros();
        transaction.setType(FILLED_SLOT);
    }

    public static int getNumberOfStoredTransactions(Tangle tangle) throws Exception {
        return tangle.getCount(Transaction.class).intValue();
    }

    public boolean update(Tangle tangle, String item) throws Exception {
        getAddressHash();
        getTrunkTransactionHash();
        getBranchTransactionHash();
        getBundleHash();
        getTagValue();
        getObsoleteTagValue();
        setAttachmentData();
        setMetadata();
        if(hash.equals(Hash.NULL_HASH)) {
            return false;
        }
        return tangle.update(transaction, hash, item);
    }

    public TransactionViewModel getBranchTransaction(Tangle tangle) throws Exception {
        if(branch == null) {
            branch = TransactionViewModel.fromHash(tangle, getBranchTransactionHash());
        }
        return branch;
    }

    public TransactionViewModel getTrunkTransaction(Tangle tangle) throws Exception {
        if(trunk == null) {
            trunk = TransactionViewModel.fromHash(tangle, getTrunkTransactionHash());
        }
        return trunk;
    }

    public static int[] trits(byte[] transactionBytes) {
        int[] trits;
        trits = new int[TRINARY_SIZE];
        if(transactionBytes != null) {
            Converter.getTrits(transactionBytes, trits);
        }
        return trits;
    }

    public synchronized int[] trits() {
        return (trits == null) ? (trits = trits(transaction.getBytes())) : trits;
    }

    public void delete(Tangle tangle) throws Exception {
        tangle.delete(Transaction.class, hash);
    }

    public List<Pair<Indexable, Persistable>> getMetadataSaveBatch() throws Exception {
        List<Pair<Indexable, Persistable>> hashesList = new ArrayList<>();
        hashesList.add(new Pair<>(getAddressHash(), new Address(hash)));
        hashesList.add(new Pair<>(getBundleHash(), new Bundle(hash)));
        hashesList.add(new Pair<>(getBranchTransactionHash(), new Approvee(hash)));
        hashesList.add(new Pair<>(getTrunkTransactionHash(), new Approvee(hash)));
        hashesList.add(new Pair<>(getObsoleteTagValue(), new ObsoleteTag(hash)));
        hashesList.add(new Pair<>(getTagValue(), new Tag(hash)));
        setAttachmentData();
        setMetadata();
        return hashesList;
    }

    public List<Pair<Indexable, Persistable>> getSaveBatch() throws Exception {
        List<Pair<Indexable, Persistable>> hashesList = new ArrayList<>();
        hashesList.addAll(getMetadataSaveBatch());
        getBytes();
        hashesList.add(new Pair<>(hash, transaction));
        return hashesList;
    }


    public static TransactionViewModel first(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> transactionPair = tangle.getFirst(Transaction.class, Hash.class);
        if(transactionPair != null && transactionPair.getHigh() != null) {
            return new TransactionViewModel((Transaction) transactionPair.getHigh(), (Hash) transactionPair.getLow());
        }
        return null;
    }

    public TransactionViewModel next(Tangle tangle) throws Exception {
        Pair<Indexable, Persistable> transactionPair = tangle.next(Transaction.class, hash);
        if(transactionPair != null && transactionPair.getHigh() != null) {
            return new TransactionViewModel((Transaction) transactionPair.getHigh(), (Hash) transactionPair.getLow());
        }
        return null;
    }

    public boolean store(Tangle tangle) throws Exception {
        if (hash.equals(Hash.NULL_HASH) || exists(tangle, hash)) {
            return false;
        }

        List<Pair<Indexable, Persistable>> batch = getSaveBatch();
        if (exists(tangle, hash)) {
            return false;
        }
        return tangle.saveBatch(batch);
    }

    public ApproveeViewModel getApprovers(Tangle tangle) throws Exception {
        if(approovers == null && hash != null) {
            approovers = ApproveeViewModel.Companion.load(tangle, hash);
        }
        return approovers;
    }

    public final int getType() {
        return transaction.getType();
    }

    public void setArrivalTime(long time) {
        transaction.setArrivalTime(time);
    }

    public long getArrivalTime() {
        return transaction.getArrivalTime();
    }

    public byte[] getBytes() {
        if(transaction.getBytes() == null || transaction.getBytes().length != SIZE) {
            transaction.setBytes(new byte[SIZE]);
            if(trits != null) {
                Converter.bytes(trits(), 0, transaction.getBytes(), 0, trits().length);
            }
        }
        return transaction.getBytes();
    }

    public Hash getHash() {
        return hash;
    }

    public AddressViewModel getAddress(Tangle tangle) throws Exception {
        if(address == null) {
            address = AddressViewModel.Companion.load(tangle, getAddressHash());
        }
        return address;
    }

    public TagViewModel getTag(Tangle tangle) throws Exception {
        return TagViewModel.Companion.load(tangle, getTagValue());
    }

    public Hash getAddressHash() {
        if(transaction.getAddress() == null) {
            transaction.setAddress(new Hash(trits(), ADDRESS_TRINARY_OFFSET));
        }
        return transaction.getAddress();
    }

    public Hash getObsoleteTagValue() {
        if(transaction.getObsoleteTag() == null) {
            byte[] tagBytes = Converter.allocateBytesForTrits(OBSOLETE_TAG_TRINARY_SIZE);
            Converter.bytes(trits(), OBSOLETE_TAG_TRINARY_OFFSET, tagBytes, 0, OBSOLETE_TAG_TRINARY_SIZE);

            transaction.setObsoleteTag(new Hash(tagBytes, 0, TAG_SIZE_IN_BYTES));
        }
        return transaction.getObsoleteTag();
    }

    public Hash getBundleHash() {
        if(transaction.getBundle() == null) {
            transaction.setBundle(new Hash(trits(), BUNDLE_TRINARY_OFFSET));
        }
        return transaction.getBundle();
    }

    public Hash getTrunkTransactionHash() {
        if(transaction.getTrunk() == null) {
            transaction.setTrunk(new Hash(trits(), TRUNK_TRANSACTION_TRINARY_OFFSET));
        }
        return transaction.getTrunk();
    }

    public Hash getBranchTransactionHash() {
        if(transaction.getBranch() == null) {
            transaction.setBranch(new Hash(trits(), BRANCH_TRANSACTION_TRINARY_OFFSET));
        }
        return transaction.getBranch();
    }

    public Hash getTagValue() {
        if(transaction.getTag() == null) {
            byte[] tagBytes = Converter.allocateBytesForTrits(TAG_TRINARY_SIZE);
            Converter.bytes(trits(), TAG_TRINARY_OFFSET, tagBytes, 0, TAG_TRINARY_SIZE);
            transaction.setTag(new Hash(tagBytes, 0, TAG_SIZE_IN_BYTES));
        }
        return transaction.getTag();
    }

    public long getAttachmentTimestamp() { return transaction.getAttachmentTimestamp(); }
    public long getAttachmentTimestampLowerBound() {
        return transaction.getAttachmentTimestampLowerBound();
    }
    public long getAttachmentTimestampUpperBound() {
        return transaction.getAttachmentTimestampUpperBound();
    }


    public long value() {
        return transaction.getValue();
    }

    public void setValidity(Tangle tangle, int validity) throws Exception {
        if(transaction.getValidity() != validity) {
            transaction.setValidity(validity);
            update(tangle, "validity");
        }
    }

    public int getValidity() {
        return transaction.getValidity();
    }

    public long getCurrentIndex() {
        return transaction.getCurrentIndex();
    }

    public int[] getSignature() {
        return Arrays.copyOfRange(trits(), SIGNATURE_MESSAGE_FRAGMENT_TRINARY_OFFSET, SIGNATURE_MESSAGE_FRAGMENT_TRINARY_SIZE);
    }

    public long getTimestamp() {
        return transaction.getTimestamp();
    }

    public byte[] getNonce() {
        byte[] nonce = Converter.allocateBytesForTrits(NONCE_TRINARY_SIZE);
        Converter.bytes(trits(), NONCE_TRINARY_OFFSET, nonce, 0, trits().length);
        return nonce;
    }

    public long lastIndex() {
        return transaction.getLastIndex();
    }

    public void setAttachmentData() {
        getTagValue();
        transaction.setAttachmentTimestamp(Converter.longValue(trits(), ATTACHMENT_TIMESTAMP_TRINARY_OFFSET, ATTACHMENT_TIMESTAMP_TRINARY_SIZE));
        transaction.setAttachmentTimestampLowerBound(Converter.longValue(trits(), ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_OFFSET, ATTACHMENT_TIMESTAMP_LOWER_BOUND_TRINARY_SIZE));
        transaction.setAttachmentTimestampUpperBound(Converter.longValue(trits(), ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_OFFSET, ATTACHMENT_TIMESTAMP_UPPER_BOUND_TRINARY_SIZE));

    }
    public void setMetadata() {
        transaction.setValue(Converter.longValue(trits(), VALUE_TRINARY_OFFSET, VALUE_USABLE_TRINARY_SIZE));
        transaction.setTimestamp(Converter.longValue(trits(), TIMESTAMP_TRINARY_OFFSET, TIMESTAMP_TRINARY_SIZE));
        //if (transaction.timestamp > 1262304000000L ) transaction.timestamp /= 1000L;  // if > 01.01.2010 in milliseconds
        transaction.setCurrentIndex(Converter.longValue(trits(), CURRENT_INDEX_TRINARY_OFFSET, CURRENT_INDEX_TRINARY_SIZE));
        transaction.setLastIndex(Converter.longValue(trits(), LAST_INDEX_TRINARY_OFFSET, LAST_INDEX_TRINARY_SIZE));
        transaction.setType(transaction.getBytes() == null ? TransactionViewModel.PREFILLED_SLOT : TransactionViewModel.FILLED_SLOT);
    }

    public static boolean exists(Tangle tangle, Hash hash) throws Exception {
        return tangle.exists(Transaction.class, hash);
    }

    public static Set<Indexable> getMissingTransactions(Tangle tangle) throws Exception {
        return tangle.keysWithMissingReferences(Approvee.class, Transaction.class);
    }

    public static void updateSolidTransactions(Tangle tangle, final Set<Hash> analyzedHashes) throws Exception {
        Iterator<Hash> hashIterator = analyzedHashes.iterator();
        TransactionViewModel transactionViewModel;
        while(hashIterator.hasNext()) {
            transactionViewModel = TransactionViewModel.fromHash(tangle, hashIterator.next());

            transactionViewModel.updateHeights(tangle);

            if(!transactionViewModel.isSolid()) {
                transactionViewModel.updateSolid(true);
                transactionViewModel.update(tangle, "solid|height");
            }
        }
    }

    public boolean updateSolid(boolean solid) throws Exception {
        if(solid != transaction.getSolid()) {
            transaction.setSolid(solid);
            return true;
        }
        return false;
    }

    public boolean isSolid() {
        return transaction.getSolid();
    }

    public int snapshotIndex() {
        return transaction.getSnapshot();
    }

    public void setSnapshot(Tangle tangle, final int index) throws Exception {
        if ( index != transaction.getSnapshot()) {
            transaction.setSnapshot(index);
            update(tangle, "snapshot");
        }
    }

    public long getHeight() {
        return transaction.getHeight();
    }

    private void updateHeight(long height) throws Exception {
        transaction.setHeight(height);
    }

    public void updateHeights(Tangle tangle) throws Exception {
        TransactionViewModel transactionVM = this, trunk = this.getTrunkTransaction(tangle);
        Stack<Hash> transactionViewModels = new Stack<>();
        transactionViewModels.push(transactionVM.getHash());
        while(trunk.getHeight() == 0 && trunk.getType() != PREFILLED_SLOT && !trunk.getHash().equals(Hash.NULL_HASH)) {
            transactionVM = trunk;
            trunk = transactionVM.getTrunkTransaction(tangle);
            transactionViewModels.push(transactionVM.getHash());
        }
        while(transactionViewModels.size() != 0) {
            transactionVM = TransactionViewModel.fromHash(tangle, transactionViewModels.pop());
            long currentHeight = transactionVM.getHeight();
            if(Hash.NULL_HASH.equals(trunk.getHash()) && trunk.getHeight() == 0
                    && !Hash.NULL_HASH.equals(transactionVM.getHash())) {
                if(currentHeight != 1L ){
                    transactionVM.updateHeight(1L);
                    transactionVM.update(tangle, "height");
                }
            } else if ( trunk.getType() != PREFILLED_SLOT && transactionVM.getHeight() == 0){
                long newHeight = 1L + trunk.getHeight();
                if(currentHeight != newHeight) {
                    transactionVM.updateHeight(newHeight);
                    transactionVM.update(tangle, "height");
                }
            } else {
                break;
            }
            trunk = transactionVM;
        }
    }

    public void updateSender(String sender) throws Exception {
        transaction.setSender(sender);
    }
    public String getSender() {
        return transaction.getSender();
    }
}
