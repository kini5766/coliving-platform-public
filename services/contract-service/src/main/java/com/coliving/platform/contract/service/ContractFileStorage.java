package com.coliving.platform.contract.service;

/**
 * 서명 이미지·서명 PDF 저장 창구. DB에는 path만 남긴다.
 * 지금은 LocalContractFileStorage뿐이다. 오브젝트 스토리지가 정해지면 구현체만 추가하면 된다.
 *
 * @author 한성현
 */
public interface ContractFileStorage {

    /** 새 파일 저장. 같은 경로가 이미 있으면 실패한다 (증빙 파일 덮어쓰기 금지) */
    void save(String path, byte[] content);

    byte[] read(String path);

    /** 서명 저장이 실패했을 때 남은 파일 정리용. 없는 파일이면 아무 일도 하지 않는다 */
    void delete(String path);
}
