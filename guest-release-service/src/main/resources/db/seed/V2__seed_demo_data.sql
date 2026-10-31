INSERT INTO produced_episode (episode_id, producer_id)
VALUES ('2a3f078d-471e-46bb-a4b7-23ad33300960', '11111111-1111-1111-1111-111111111111');

INSERT INTO excerpt_reference (excerpt_id, episode_id, guest_id, code, start_ms, end_ms)
VALUES ('f0f4e4bf-dff7-433c-91be-b8b151c47e42',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        '22222222-2222-2222-2222-222222222222',
        'ex-ava-020500',
        1205000,
        1242000);

INSERT INTO excerpt_reference (excerpt_id, episode_id, guest_id, code, start_ms, end_ms)
VALUES ('6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        '22222222-2222-2222-2222-222222222222',
        'ex-ava-341000',
        2050000,
        2072000);

INSERT INTO guest_release (id, guest_id, episode_id, status, granted_at, version)
VALUES ('d76025ba-7021-47f0-ab83-4749a581fd61',
        '22222222-2222-2222-2222-222222222222',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        'ACTIVE',
        '2026-10-21T10:00:00Z',
        0);

INSERT INTO release_scope (id, release_id, excerpt_id, scope_type, scope_status, granted_at, version)
VALUES ('4ace15d0-2e39-488e-8bc1-4084a08d557a',
        'd76025ba-7021-47f0-ab83-4749a581fd61',
        'f0f4e4bf-dff7-433c-91be-b8b151c47e42',
        'PODCAST_DISTRIBUTION',
        'ACTIVE',
        '2026-10-21T10:00:00Z',
        0);

INSERT INTO release_scope (id, release_id, excerpt_id, scope_type, scope_status, granted_at, version)
VALUES ('9b1e2f3a-4c5d-4e6f-8a7b-1c2d3e4f5a60',
        'd76025ba-7021-47f0-ab83-4749a581fd61',
        '6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410',
        'PODCAST_DISTRIBUTION',
        'ACTIVE',
        '2026-10-21T10:00:00Z',
        0);
