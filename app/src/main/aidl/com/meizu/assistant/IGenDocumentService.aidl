// IGenDocumentService.aidl
package com.meizu.assistant;

interface IGenDocumentService {
    oneway void sendDocumentBytes(in byte[] documentBytes);
}
