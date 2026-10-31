INSERT INTO podcast_episode (id, code, title, producer_id, published_at, created_at)
VALUES ('2a3f078d-471e-46bb-a4b7-23ad33300960',
        'ep-2026-001',
        'Designing for Trust',
        '11111111-1111-1111-1111-111111111111',
        '2026-10-20T09:00:00Z',
        '2026-10-20T09:00:00Z');

INSERT INTO podcast_excerpt (id, episode_id, code, guest_id, start_ms, end_ms, transcript, tag_status, created_at)
VALUES ('f0f4e4bf-dff7-433c-91be-b8b151c47e42',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        'ex-ava-020500',
        '22222222-2222-2222-2222-222222222222',
        1205000,
        1242000,
        $txt$Ava Rao: Trust is designed in the small moments, not announced on a landing page.$txt$,
        'ACTIVE',
        '2026-10-20T09:00:00Z');

INSERT INTO podcast_excerpt (id, episode_id, code, guest_id, start_ms, end_ms, transcript, tag_status, created_at)
VALUES ('6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        'ex-ava-341000',
        '22222222-2222-2222-2222-222222222222',
        2050000,
        2072000,
        $txt$Ava Rao: The best feedback loops make it safe to say the uncomfortable thing early.$txt$,
        'ACTIVE',
        '2026-10-20T09:00:00Z');
