import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Chip, InputAdornment, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination,
  TableRow, TextField } from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import PersonAddOutlined from '@mui/icons-material/PersonAddOutlined';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import type { Member, Page } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Empty, Loading, PageHeader } from '../../components/common';
import { faDigits, formatDate } from '../../utils/format';
import { MemberFormDialog } from './MemberFormDialog';

export default function MembersPage() {
  const navigate = useNavigate();
  const { hasRole } = useAuth();
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [open, setOpen] = useState(false);
  const { data, isLoading } = useQuery({
    queryKey: ['members', q, page, size],
    queryFn: () => api.get<Page<Member>>('/members', { params: { q, page, size } }).then((r) => r.data),
    placeholderData: keepPreviousData,
  });
  return (
    <>
      <PageHeader title="اعضا" subtitle={data ? `${faDigits(data.totalElements)} عضو` : undefined}
        actions={hasRole('ADMIN', 'RECEPTIONIST') && (
          <Button variant="contained" startIcon={<PersonAddOutlined />} onClick={() => setOpen(true)}>عضو جدید</Button>
        )} />
      <Card>
        <TextField sx={{ p: 2 }} placeholder="جستجو با نام، موبایل، شماره عضویت، کارت یا کد ملی" value={q}
          onChange={(e) => { setQ(e.target.value); setPage(0); }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> }} />
        {isLoading ? <Loading /> : !data?.content.length ? <Empty text="عضوی یافت نشد" /> : (
          <>
            <TableContainer>
              <Table>
                <TableHead><TableRow>
                  <TableCell>شماره عضویت</TableCell><TableCell>نام</TableCell><TableCell>موبایل</TableCell>
                  <TableCell>مربی</TableCell><TableCell>تاریخ عضویت</TableCell><TableCell>وضعیت</TableCell>
                </TableRow></TableHead>
                <TableBody>
                  {data.content.map((m) => (
                    <TableRow key={m.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/members/${m.id}`)}>
                      <TableCell>{faDigits(m.membershipNo)}</TableCell>
                      <TableCell>{m.fullName}</TableCell>
                      <TableCell>{faDigits(m.phone)}</TableCell>
                      <TableCell>{m.coachName ?? '—'}</TableCell>
                      <TableCell>{formatDate(m.createdAt)}</TableCell>
                      <TableCell>{m.active ? <Chip size="small" label="فعال" color="success" variant="outlined" /> : <Chip size="small" label="غیرفعال" />}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
            <TablePagination component="div" count={data.totalElements} page={page} rowsPerPage={size}
              onPageChange={(_, p) => setPage(p)} onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}
              labelRowsPerPage="تعداد در صفحه" labelDisplayedRows={({ from, to, count }) => `${faDigits(from)}–${faDigits(to)} از ${faDigits(count)}`} />
          </>
        )}
      </Card>
      <MemberFormDialog open={open} onClose={() => setOpen(false)} onSaved={(m) => navigate(`/members/${m.id}`)} />
    </>
  );
}
