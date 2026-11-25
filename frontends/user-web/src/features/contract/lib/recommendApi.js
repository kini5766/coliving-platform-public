import { ApiError } from './api';

// ---------------------------------------------------------------
// 1-1 호실 선택 화면의 "다른 방 추천" API
//
// 추천 순서
//   1. 같은 건물의 다른 방
//   2. (1이 없으면) 같은 지역의 다른 건물 방
//   3. (2도 없으면) 추천 없음 → 화면이 "다른 지역 둘러보기" 링크를 보여줌
//      거리순 추천은 매물 서비스에 건물 좌표(위도·경도)가 생기면 추가한다
//
// 후보 방 목록은 매물 서비스(GET /property-service/property/**, 비회원 허용)에서 받고,
// 고른 기간에 비어 있는지는 계약 서비스 한 번 호출로 확인한다.
//
// api.js와 같은 규칙(목업 전환, gateway 주소, 개발용 X-User)을 쓰지만,
// api.js를 건드리지 않으려고 이 파일에 따로 둔다.
// ---------------------------------------------------------------
const USE_MOCK = import.meta.env.VITE_USE_MOCK === '1' || import.meta.env.VITE_PREVIEW === '1';
const DEV_USER_ID = import.meta.env.VITE_DEV_USER_ID;
const GATEWAY = import.meta.env.VITE_BACKEND_API_BASE_URL ?? '';
const CONTRACT_BASE = `${GATEWAY}/contract-service`;
const PROPERTY_BASE = `${GATEWAY}/property-service`;

// 같은 지역에서 상세를 불러올 건물 수 상한 (건물마다 상세 API를 1번씩 부르므로)
export const REGION_BUILDING_LIMIT = 8;

async function get(base, path) {
  let res;
  try {
    res = await fetch(base + path, {
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        ...(DEV_USER_ID && base === CONTRACT_BASE && { 'X-User': DEV_USER_ID }),
      },
    });
  } catch {
    throw new ApiError(0, '서버에 연결할 수 없어요.');
  }
  if (!res.ok) throw new ApiError(res.status, `요청에 실패했어요. (${res.status})`);
  return res.json();
}

const mock = (value, ms = 300) => new Promise((resolve) => setTimeout(() => resolve(value), ms));

// 매물 RoomSummary + 건물 정보 → 추천 카드 한 장에 필요한 값
const toCandidate = (building, room) => ({
  roomId: room.id,
  roomName: room.roomName,
  roomType: room.roomType,
  floor: room.floor,
  area: room.areaM2,
  monthlyFee: room.monthlyFee,
  deposit: room.deposit,
  buildingId: building.id,
  buildingName: building.name,
});

// ---------------------------------------------------------------
// 건물 상세 (방 목록 포함). 같은 화면에서 날짜만 바뀔 때 다시 부르지 않도록 캐시한다
const buildingCache = new Map();

const MOCK_BUILDING = (id) => ({
  id,
  name: `목업 건물 ${id}`,
  region: '서울 마포구',
  rooms: [1, 2, 3, 4].map((n) => ({
    id: id * 100 + n,
    roomName: `${n + 1}0${n}호`,
    roomType: n % 2 ? '1인실' : '2인실',
    floor: n + 1,
    areaM2: 12 + n,
    monthlyFee: 600000 + n * 50000,
    deposit: 1000000,
  })),
});

export function getBuilding(buildingId) {
  if (!buildingCache.has(buildingId)) {
    const p = USE_MOCK
      ? mock(MOCK_BUILDING(Number(buildingId)))
      : get(PROPERTY_BASE, `/property/buildings/${encodeURIComponent(buildingId)}`);
    // 실패한 요청은 캐시에서 지워서 다음에 다시 시도하게 한다
    buildingCache.set(buildingId, p.catch((e) => { buildingCache.delete(buildingId); throw e; }));
  }
  return buildingCache.get(buildingId);
}

// 같은 지역의 건물 목록 (지금 건물 제외, 상한 REGION_BUILDING_LIMIT)
const regionCache = new Map();

export function getRegionBuildingIds(region, excludeBuildingId) {
  if (!region) return Promise.resolve([]);
  if (!regionCache.has(region)) {
    const p = USE_MOCK
      ? mock({ content: [{ id: 2 }, { id: 3 }] })
      : get(PROPERTY_BASE, `/property/buildings?region=${encodeURIComponent(region)}&page=0&size=${REGION_BUILDING_LIMIT + 1}`);
    regionCache.set(region, p.catch((e) => { regionCache.delete(region); throw e; }));
  }
  return regionCache.get(region).then((page) =>
    (page.content ?? [])
      .map((b) => b.id)
      .filter((id) => String(id) !== String(excludeBuildingId))
      .slice(0, REGION_BUILDING_LIMIT),
  );
}

// ---------------------------------------------------------------
// [계약 서비스] GET /apply/rooms/available?roomIds=..&moveInDate=..&months=..
//   → { moveInDate, moveOutDate, availableRoomIds: [..] } (보낸 순서 유지)
export function getAvailableRoomIds(roomIds, moveInDate, months) {
  if (roomIds.length === 0) return Promise.resolve([]);
  if (USE_MOCK) return mock(roomIds.filter((id) => id % 3 !== 0)); // 목업: 3의 배수 호실은 예약 있음
  const q = new URLSearchParams({ roomIds: roomIds.join(','), moveInDate, months: String(months) });
  return get(CONTRACT_BASE, `/apply/rooms/available?${q}`).then((res) => res.availableRoomIds ?? []);
}

// ---------------------------------------------------------------
// 비슷한 방을 앞에: 같은 방 타입 먼저, 그다음 월 이용료 차이가 작은 순
const bySimilarity = (base) => (x, y) => {
  const typeX = x.roomType === base.roomType ? 0 : 1;
  const typeY = y.roomType === base.roomType ? 0 : 1;
  if (typeX !== typeY) return typeX - typeY;
  return Math.abs(x.monthlyFee - base.monthlyFee) - Math.abs(y.monthlyFee - base.monthlyFee);
};

async function availableOf(candidates, base, moveInDate, months) {
  const ids = new Set(await getAvailableRoomIds(candidates.map((c) => c.roomId), moveInDate, months));
  return candidates.filter((c) => ids.has(c.roomId)).sort(bySimilarity(base));
}

/**
 * 추천 방 찾기.
 * @param room 지금 보고 있는 방 (getRoom 결과: roomId, buildingId, roomType, monthlyFee)
 * @param moveInDate "YYYY-MM-DD"
 * @param months 3 | 6 | 12
 * @returns {{ tier: 'building' | 'region' | 'none', region: string, items: object[] }}
 */
export async function findRecommendations(room, moveInDate, months) {
  // 목업 ROOM에는 buildingId가 없어서 1번 건물로 본다. 실제 응답(RoomDetail)에는 항상 있다
  const buildingId = room.buildingId ?? (USE_MOCK ? 1 : null);
  if (buildingId == null) return { tier: 'none', region: '', items: [] };
  room = { ...room, buildingId };

  const building = await getBuilding(buildingId);
  const region = building.region ?? '';

  // 1. 같은 건물
  const sameBuilding = (building.rooms ?? [])
    .filter((r) => String(r.id) !== String(room.roomId))
    .map((r) => toCandidate(building, r));
  const tier1 = await availableOf(sameBuilding, room, moveInDate, months);
  if (tier1.length > 0) return { tier: 'building', region, items: tier1 };

  // 2. 같은 지역의 다른 건물 (건물 상세를 나란히 불러온다. 하나가 실패해도 나머지로 진행)
  const ids = await getRegionBuildingIds(region, room.buildingId);
  const details = await Promise.allSettled(ids.map((id) => getBuilding(id)));
  const regionRooms = details
    .filter((d) => d.status === 'fulfilled')
    .flatMap((d) => (d.value.rooms ?? []).map((r) => toCandidate(d.value, r)));
  const tier2 = await availableOf(regionRooms.slice(0, 200), room, moveInDate, months); // 서버 상한 200
  if (tier2.length > 0) return { tier: 'region', region, items: tier2 };

  return { tier: 'none', region, items: [] };
}
