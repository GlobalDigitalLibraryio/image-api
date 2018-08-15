-- 1. Change licenseformat
update imagemetadata as imNew set
metadata = jsonb_set(imNew.metadata, '{copyright, license}', imOld.metadata->'copyright'->'license'->'license', false)
from imagemetadata as imOld
where imNew.id = imOld.id;

-- 2. Change identifier to match spdx-standard
update imagemetadata set metadata = jsonb_set(metadata, '{copyright, license}', '"CC-BY-2.0"', false) where metadata->'copyright'->>'license' = 'by';
update imagemetadata set metadata = jsonb_set(metadata, '{copyright, license}', '"CC-BY-3.0"', false) where metadata->'copyright'->>'license' = 'by-3.0';
update imagemetadata set metadata = jsonb_set(metadata, '{copyright, license}', '"CC-BY-4.0"', false) where metadata->'copyright'->>'license' = 'by-4.0';
update imagemetadata set metadata = jsonb_set(metadata, '{copyright, license}', '"CC-BY-NC-3.0"', false) where metadata->'copyright'->>'license' = 'by-nc-3.0';
update imagemetadata set metadata = jsonb_set(metadata, '{copyright, license}', '"CC-BY-NC-3.0"', false) where metadata->'copyright'->>'license' = 'by-nc-4.0';
update imagemetadata set metadata = jsonb_set(metadata, '{copyright, license}', '"CC-BY-NC-SA-4.0"', false) where metadata->'copyright'->>'license' = 'by-nc-sa-4.0';
update imagemetadata set metadata = jsonb_set(metadata, '{copyright, license}', '"CC-BY-ND-4.0"', false) where metadata->'copyright'->>'license' = 'by-nd-4.0';
update imagemetadata set metadata = jsonb_set(metadata, '{copyright, license}', '"CC-BY-SA-2.0"', false) where metadata->'copyright'->>'license' = 'by-sa';
update imagemetadata set metadata = jsonb_set(metadata, '{copyright, license}', '"CC-BY-SA-3.0"', false) where metadata->'copyright'->>'license' = 'by-sa-3.0';
update imagemetadata set metadata = jsonb_set(metadata, '{copyright, license}', '"CC-BY-SA-4.0"', false) where metadata->'copyright'->>'license' = 'by-sa-4.0';

-- We have only one image in test with this license, and that is testdata. Copyrighted should not occur in GDL.
update imagemetadata set metadata = jsonb_set(metadata, '{copyright, license}', '"CC-BY-4.0"', false) where metadata->'copyright'->>'license' = 'copyrighted';