// Block Chain should maintain only limited block nodes to satisfy the functions
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.
import java.util.ArrayList;
import java.sql.Timestamp;

public class BlockChain {
    public static final int CUT_OFF_AGE = 10;
    private TransactionPool _transPool = new TransactionPool();
    private ArrayList<BlockNode> _blockchain = new ArrayList<>();
    private BlockNode _maxHeightNode;

    public void updateMaxHeightNode() {
        BlockNode currentMaxHeightNode = _maxHeightNode;
        for (BlockNode b : _blockchain) {
            if (b._height > currentMaxHeightNode._height) {
                currentMaxHeightNode = b;
            //if b is same height as current last block, currentMaxHeightNode is the block that came earlier.
            } else if (b._height == currentMaxHeightNode._height) {
                if (currentMaxHeightNode._createAt.after(b._createAt)) {
                    currentMaxHeightNode = b;
                }
            }
        }
        _maxHeightNode = currentMaxHeightNode;
    }

    //blockhash is an arbitrary has value hash (which typically found in hash pointer of a block's header, thus getParentNode).
    public BlockNode getParentNode(byte[] blockHash) {
        ByteArrayWrapper b1 = new ByteArrayWrapper(blockHash);
        //for each block in the blockchain, compare its hash to blockhash.
        for (BlockNode b : _blockchain) {
            ByteArrayWrapper b2 = new ByteArrayWrapper(b._block.getHash());
            if (b1.equals(b2)) {
                return b;
            }
        }
        return null;
    }

    //height is the position of the latest block.
    public class BlockNode {
        private Block _block;
        private int _height = 0;
        private UTXOPool _utxoPool = new UTXOPool();
        private TransactionPool _transPool = new TransactionPool();
        private Timestamp _createAt;

        public BlockNode(Block block, int height, UTXOPool utxoPool, TransactionPool transPool) {
            this._block = block;
            this._height = height;
            this._utxoPool = utxoPool;
            this._transPool = transPool;
            this._createAt = new Timestamp(System.currentTimeMillis());
        }

        public UTXOPool getUTXOPool() {
            return this._utxoPool;
        }

        public TransactionPool getTransactionPool() {
            return this._transPool;
        }
    }

    /**
     * create an empty block chain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    public BlockChain(Block genesisBlock) {
        UTXOPool utxoPool = new UTXOPool();
        TransactionPool transPool = new TransactionPool();
        //for each output in coinbase transaction, add unspent coin into the pool.
        for (int i = 0; i < genesisBlock.getCoinbase().numOutputs(); i++) {
            utxoPool.addUTXO(new UTXO(genesisBlock.getCoinbase().getHash(),i), genesisBlock.getCoinbase().getOutput(i));
        }
        transPool.addTransaction(genesisBlock.getCoinbase());
        //for each transactions in the genesis block,
        for (Transaction t : genesisBlock.getTransactions()) {
            if (t != null) {
                //for each output in a transaction, creat new unspent coin and add to the pool.
                for (int i=0; i<t.numOutputs(); i++) {
                    Transaction.Output output = t.getOutput(i);
                    UTXO utxo = new UTXO(t.getHash(),i);
                    utxoPool.addUTXO(utxo,output);
                }
                transPool.addTransaction(t);
            }
        }
        BlockNode b = new BlockNode(genesisBlock, 1, utxoPool, transPool);
        _maxHeightNode = b;
        _blockchain.add(b);
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        return _maxHeightNode._block;
    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        return _maxHeightNode._utxoPool;
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        return _transPool;
    }

    /**
     * Add {@code block} to the block chain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}.
     * 
     * <p>
     * For example, you can try creating a new block over the genesis block (block height 2) if the
     * block chain height is {@code <=
     * CUT_OFF_AGE + 1}. As soon as {@code height > CUT_OFF_AGE + 1}, you cannot create a new block
     * at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        //check block is genesisBlock?
        if (block.getPrevBlockHash() == null) {
            return false;
        }
        //check parent Hash
        BlockNode parentNode = getParentNode(block.getPrevBlockHash());
        if(parentNode == null) {
            return false;
        }
        //if this block height <= current height - CUT_OFF_AGE, do not add block.
        int blockHeight = parentNode._height+1;
        if (blockHeight <= _maxHeightNode._height - CUT_OFF_AGE, do not add block.) {
            return false;
        }

        //check all transactions in block are valid?
        //get pools from previous block.
        UTXOPool utxoPool = new UTXOPool(parentNode.getUTXOPool());
        TransactionPool transPool = new TransactionPool(parentNode.getTransactionPool());
        //for each transaction in this block,
        for (Transaction t : block.getTransactions()) {
            TxHandler txHandler = new TxHandler(utxoPool);
            if (!txHandler.isValidTx(t)) {
                return false;
            }
            //for each input in a transaction, remove it from current unspent transactions pool.
            for (Transaction.Input input : t.getInputs()) {
                int outputIndex = input.outputIndex;
                byte[] prevTxHash = input.prevTxHash;
                UTXO utxo = new UTXO(prevTxHash, outputIndex);
                utxoPool.removeUTXO(utxo);
            }
            //for each output in a transaction, add it to current unspent transactions pool.
            byte[] hash = t.getHash();
            for (int i=0;i<t.numOutputs();i++) {
                UTXO utxo = new UTXO(hash, i);
                utxoPool.addUTXO(utxo, t.getOutput(i));
            }
        }

        //add coinbase to current unspent transactions pool.
        for (int i = 0; i < block.getCoinbase().numOutputs(); i++) {
            utxoPool.addUTXO(new UTXO(block.getCoinbase().getHash(),i),block.getCoinbase().getOutput(i));
        }

        //remove transactions pool
        for (Transaction t : block.getTransactions()) {
            transPool.removeTransaction(t.getHash());
        }

        //add new block
        BlockNode b = new BlockNode(block,blockHeight,utxoPool,transPool);
        boolean addNewBlock = _blockchain.add(b); //add new block to _blockchain (arrayList) and assign true to addNewBlock (ArrayList always accept elements, so always return true).
        if (addNewBlock) {
            updateMaxHeightNode();
        }
        return addNewBlock;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        _transPool.addTransaction(tx);
    }
}