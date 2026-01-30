// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

/// @notice Minimal N-of-M multisig wallet (Council).
/// @dev Designed for permissioned deployments (Besu/Quorum) where governance must not rely on a single EOA.
contract MultiSigCouncil {
    event Deposit(address indexed sender, uint256 amount, uint256 balance);

    event Submit(uint256 indexed txId, address indexed proposer, address indexed to, uint256 value, bytes data);
    event Confirm(address indexed owner, uint256 indexed txId);
    event Revoke(address indexed owner, uint256 indexed txId);
    event Execute(uint256 indexed txId, address indexed executor, bool success, bytes result);

    error NotOwner();
    error TxDoesNotExist();
    error TxAlreadyExecuted();
    error TxAlreadyConfirmed();
    error TxNotConfirmed();
    error NotEnoughConfirmations();
    error InvalidOwners();
    error InvalidThreshold();

    struct Transaction {
        address to;
        uint256 value;
        bytes data;
        bool executed;
        uint256 confirmations;
    }

    address[] public owners;
    mapping(address => bool) public isOwner;
    uint256 public threshold; // confirmations required

    Transaction[] public transactions;
    mapping(uint256 => mapping(address => bool)) public confirmedBy;

    modifier onlyOwner() {
        if (!isOwner[msg.sender]) revert NotOwner();
        _;
    }

    constructor(address[] memory owners_, uint256 threshold_) {
        if (owners_.length == 0) revert InvalidOwners();
        if (threshold_ == 0 || threshold_ > owners_.length) revert InvalidThreshold();

        for (uint256 i = 0; i < owners_.length; i++) {
            address o = owners_[i];
            if (o == address(0) || isOwner[o]) revert InvalidOwners();
            isOwner[o] = true;
            owners.push(o);
        }

        threshold = threshold_;
    }

    receive() external payable {
        emit Deposit(msg.sender, msg.value, address(this).balance);
    }

    function transactionsCount() external view returns (uint256) {
        return transactions.length;
    }

    function submitTransaction(address to, uint256 value, bytes calldata data) external onlyOwner returns (uint256 txId) {
        txId = transactions.length;
        transactions.push(Transaction({to: to, value: value, data: data, executed: false, confirmations: 0}));
        emit Submit(txId, msg.sender, to, value, data);
    }

    function confirmTransaction(uint256 txId) external onlyOwner {
        if (txId >= transactions.length) revert TxDoesNotExist();
        Transaction storage t = transactions[txId];
        if (t.executed) revert TxAlreadyExecuted();
        if (confirmedBy[txId][msg.sender]) revert TxAlreadyConfirmed();

        confirmedBy[txId][msg.sender] = true;
        t.confirmations += 1;
        emit Confirm(msg.sender, txId);
    }

    function revokeConfirmation(uint256 txId) external onlyOwner {
        if (txId >= transactions.length) revert TxDoesNotExist();
        Transaction storage t = transactions[txId];
        if (t.executed) revert TxAlreadyExecuted();
        if (!confirmedBy[txId][msg.sender]) revert TxNotConfirmed();

        confirmedBy[txId][msg.sender] = false;
        t.confirmations -= 1;
        emit Revoke(msg.sender, txId);
    }

    function executeTransaction(uint256 txId) external onlyOwner returns (bool success, bytes memory result) {
        if (txId >= transactions.length) revert TxDoesNotExist();
        Transaction storage t = transactions[txId];
        if (t.executed) revert TxAlreadyExecuted();
        if (t.confirmations < threshold) revert NotEnoughConfirmations();

        t.executed = true;
        (success, result) = t.to.call{value: t.value}(t.data);
        emit Execute(txId, msg.sender, success, result);
    }
}

