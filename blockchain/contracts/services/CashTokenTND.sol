// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import {Ownable} from "@openzeppelin/contracts/access/Ownable.sol";

/// @notice Internal on-chain TND token used as "cash wallet" inside the permissioned chain.
/// @dev Mint/Burn are platform-controlled (owner).
contract CashTokenTND is ERC20, Ownable {
    constructor(address owner_) ERC20("FAN TND", "TND") Ownable(owner_) {}

    function decimals() public pure override returns (uint8) {
        return 8;
    }

    function mint(address to, uint256 amount) external onlyOwner {
        _mint(to, amount);
    }

    function burn(address from, uint256 amount) external onlyOwner {
        _burn(from, amount);
    }
}

