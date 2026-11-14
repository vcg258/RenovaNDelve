-- 악마의 계약서: 원정에 걸린 계약 하나를 "혜택:조항:남은횟수" 한 줄로 둔다. 없으면 NULL.
-- 조항은 화면에 보내지 않는 서버 전용 정보다. 서약(pacts)과 같은 방식으로 게임 행에 둔다.

ALTER TABLE public.game_state ADD COLUMN contract character varying(60);
