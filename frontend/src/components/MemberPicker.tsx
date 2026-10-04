import { useState } from 'react';
import { Autocomplete, TextField } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { Member, Page } from '../api/types';
import { faDigits } from '../utils/format';
import { useDebounced } from '../hooks/useDebounced';

export function MemberPicker({ value, onChange, label = 'انتخاب عضو' }: {
  value: Member | null; onChange: (m: Member | null) => void; label?: string;
}) {
  const [input, setInput] = useState('');
  const query = useDebounced(input.trim());
  const { data, isFetching } = useQuery({
    queryKey: ['member-search', query],
    queryFn: () => api.get<Page<Member>>('/members', { params: { q: query, size: 15 } }).then((r) => r.data.content),
  });
  return (
    <Autocomplete
      value={value}
      onChange={(_, v) => onChange(v)}
      inputValue={input}
      onInputChange={(_, v) => setInput(v)}
      options={data ?? []}
      loading={isFetching}
      filterOptions={(x) => x}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      getOptionLabel={(m) => `${m.fullName} — ${faDigits(m.phone)}`}
      noOptionsText="عضوی یافت نشد"
      renderInput={(params) => <TextField {...params} label={label} placeholder="نام، موبایل یا شماره عضویت" />}
    />
  );
}
