import { InjectionToken } from '@angular/core';

/**
 * Base URL of the backend API that interfaces with the blockchain.
 * Example: http://localhost:8080/api/blockchain
 */
export const BLOCKCHAIN_API_BASE_URL = new InjectionToken<string>('BLOCKCHAIN_API_BASE_URL', {
  // Use a relative URL so Angular dev-server / mocks can handle it.
  // When Spring Boot is ready, you can override this provider to point to http://localhost:8080/api/blockchain.
  factory: () => '/api/blockchain',
});

