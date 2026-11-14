-- 아이디 찾기 / 비밀번호 찾기를 위해 회원의 이름과 이메일을 받는다.
--
-- 기존 계정에는 값이 없으므로 NULL을 허용한다 (내 정보 화면에서 등록).
-- 이메일은 소문자로 정규화해 저장하고, 값이 있는 행끼리만 중복을 막는다.
-- (PostgreSQL의 유니크 인덱스는 NULL끼리 충돌하지 않지만, 의도를 드러내려고 부분 인덱스로 둔다)

ALTER TABLE public.member ADD COLUMN name character varying(50);
ALTER TABLE public.member ADD COLUMN email character varying(254);

CREATE UNIQUE INDEX uk_member_email ON public.member (email) WHERE email IS NOT NULL;
