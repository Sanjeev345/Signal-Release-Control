INSERT INTO episode_owner (episode_id, producer_id)
VALUES ('2a3f078d-471e-46bb-a4b7-23ad33300960', '11111111-1111-1111-1111-111111111111');

INSERT INTO excerpt_guest (excerpt_id, episode_id, guest_id)
VALUES ('f0f4e4bf-dff7-433c-91be-b8b151c47e42',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        '22222222-2222-2222-2222-222222222222');

INSERT INTO excerpt_guest (excerpt_id, episode_id, guest_id)
VALUES ('6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        '22222222-2222-2222-2222-222222222222');

INSERT INTO distributed_edit (id, code, episode_id, provider, external_reference, distribution_status, distributed_at)
VALUES ('e0000000-0000-4000-8000-000000000001',
        'edit-youtube-001',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        'YouTube',
        'yt-designing-for-trust-full',
        'DISTRIBUTED',
        '2026-10-21T12:00:00Z');

INSERT INTO distributed_edit (id, code, episode_id, provider, external_reference, distribution_status, distributed_at)
VALUES ('e0000000-0000-4000-8000-000000000002',
        'edit-spotify-001',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        'Spotify',
        'spotify-designing-for-trust-audio',
        'DISTRIBUTED',
        '2026-10-21T12:05:00Z');

INSERT INTO distributed_edit (id, code, episode_id, provider, external_reference, distribution_status, distributed_at)
VALUES ('e0000000-0000-4000-8000-000000000003',
        'edit-social-001',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        'Social',
        'social-designing-for-trust-teaser',
        'DISTRIBUTED',
        '2026-10-21T12:10:00Z');

INSERT INTO distributed_edit (id, code, episode_id, provider, external_reference, distribution_status, distributed_at)
VALUES ('e0000000-0000-4000-8000-000000000004',
        'edit-tiktok-001',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        'TikTok',
        'tiktok-designing-for-trust-clip',
        'DISTRIBUTED',
        '2026-10-21T12:15:00Z');

INSERT INTO distributed_edit (id, code, episode_id, provider, external_reference, distribution_status, distributed_at)
VALUES ('e0000000-0000-4000-8000-000000000005',
        'edit-website-001',
        '2a3f078d-471e-46bb-a4b7-23ad33300960',
        'Website',
        'site-designing-for-trust-embed',
        'DISTRIBUTED',
        '2026-10-21T12:20:00Z');

INSERT INTO edit_excerpt (edit_id, excerpt_id)
VALUES ('e0000000-0000-4000-8000-000000000001', 'f0f4e4bf-dff7-433c-91be-b8b151c47e42');

INSERT INTO edit_excerpt (edit_id, excerpt_id)
VALUES ('e0000000-0000-4000-8000-000000000001', '6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410');

INSERT INTO edit_excerpt (edit_id, excerpt_id)
VALUES ('e0000000-0000-4000-8000-000000000002', 'f0f4e4bf-dff7-433c-91be-b8b151c47e42');

INSERT INTO edit_excerpt (edit_id, excerpt_id)
VALUES ('e0000000-0000-4000-8000-000000000003', '6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410');

INSERT INTO edit_excerpt (edit_id, excerpt_id)
VALUES ('e0000000-0000-4000-8000-000000000004', 'f0f4e4bf-dff7-433c-91be-b8b151c47e42');

INSERT INTO edit_excerpt (edit_id, excerpt_id)
VALUES ('e0000000-0000-4000-8000-000000000004', '6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410');

INSERT INTO edit_excerpt (edit_id, excerpt_id)
VALUES ('e0000000-0000-4000-8000-000000000005', '6f0d5a2e-7c34-4b8b-9c31-2d1b7a9e3410');
