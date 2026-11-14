-- 왕국의 저울: 원정을 시작할 때의 왕국 정세를 그 원정에 고정해 둔다.
-- 정세는 지난 7일 동안 모든 플레이어가 조우에서 택한 방식(성향)으로 정해지고, 원정 도중에는 바뀌지 않는다.
-- 예전 원정은 NULL (정세 효과 없음).

ALTER TABLE public.game_state ADD COLUMN kingdom character varying(20);
